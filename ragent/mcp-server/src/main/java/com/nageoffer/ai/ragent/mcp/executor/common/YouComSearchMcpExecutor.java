/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.mcp.executor.common;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.mcp.config.McpToolAnnotations;
import com.nageoffer.ai.ragent.mcp.executor.McpToolException;
import com.nageoffer.ai.ragent.mcp.executor.McpToolResults;
import com.nageoffer.ai.ragent.mcp.executor.McpToolSchema;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.nageoffer.ai.ragent.mcp.executor.McpToolSchema.string;
import static com.nageoffer.ai.ragent.mcp.executor.McpToolSchema.integer;

/**
 * You.com web-search MCP tool
 * <p>
 * Calls the You.com Search API (GET <a href="https://ydc-index.io/v1/search">...</a>, X-API-Key auth)
 * and returns web and news results with source links and excerpts. The API key is read from YDC_API_KEY
 * <p>
 * The tool is registered only when YDC_API_KEY is set ({@code @ConditionalOnProperty}): the tool list is a capability catalog for the LLM.
 * Registering a tool that cannot run without a key only tempts the model to call it, fail, and pollute the catalog, so "the tool exists iff it is usable",
 * aligned with the bootstrap channel's "no key means disabled" (a key that dies at runtime is still caught by the check inside handleCall)
 * <p>
 * Note: mcp-server is a zero-internal-dependency service that can be deployed alone (it does not depend on bootstrap / framework and does not share their JVM; see each module pom).
 * The compact You.com HTTP call here is therefore an intentional duplicate of bootstrap's {@code YouComWebSearchChannel}.
 * A shared module would break that isolation, so this is treated as service-level duplication. Changes to the You.com contract (endpoint / parameters / response shape) must be applied in both places
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "YDC_API_KEY")
public class YouComSearchMcpExecutor {

    private static final String TOOL_ID = "youcom_search";

    /**
     * API key environment variable name (team convention, do not change)
     */
    private static final String ENV_API_KEY = "YDC_API_KEY";

    private static final int DEFAULT_COUNT = 5;

    private static final int MAX_COUNT = 20;

    private static final List<String> FRESHNESS_VALUES = List.of("day", "week", "month", "year");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * You.com Search API URL (testability: unit tests can point it at a local stub)
     */
    String apiUrl = "https://ydc-index.io/v1/search";

    @Bean
    public McpServerFeatures.SyncToolSpecification youComSearchToolSpecification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(buildTool())
                .callHandler((exchange, request) -> handleCall(request))
                .build();
    }

    private Tool buildTool() {
        JsonSchema inputSchema = McpToolSchema.object()
                .required(string("query", "检索关键词或问题"))
                .optional(integer("count", "最多返回的结果条数（网页+新闻合计），默认 5，最大 20")
                        .defaultTo(5))
                .optional(string("freshness", "结果时效过滤：day(一天内)、week(一周内)、month(一月内)、year(一年内)，不传则不限")
                        .options(FRESHNESS_VALUES))
                .build();

        return Tool.builder()
                .name(TOOL_ID)
                .description("基于 You.com Search API 的联网搜索，返回带来源链接和摘录片段的网页与新闻结果。需要配置 YDC_API_KEY 环境变量")
                .inputSchema(inputSchema)
                .annotations(McpToolAnnotations.READ_ONLY)
                .build();
    }

    CallToolResult handleCall(CallToolRequest request) {
        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> args = McpToolResults.args(request);
            String query = MapUtil.getStr(args, "query");
            Integer count = MapUtil.getInt(args, "count");
            String freshness = MapUtil.getStr(args, "freshness");

            if (StrUtil.isBlank(query)) {
                return McpToolResults.error("Provide a search query");
            }
            if (count == null || count <= 0) count = DEFAULT_COUNT;
            if (count > MAX_COUNT) count = MAX_COUNT;
            if (StrUtil.isNotBlank(freshness) && !FRESHNESS_VALUES.contains(freshness)) {
                return McpToolResults.error("freshness is invalid. Allowed values: " + String.join("、", FRESHNESS_VALUES));
            }

            String apiKey = readEnv(ENV_API_KEY);
            if (StrUtil.isBlank(apiKey)) {
                return McpToolResults.error("You.com web search is not configured. Set the YDC_API_KEY environment variable"
                        + " (create one at https://you.com/platform/api-keys), then restart the MCP server");
            }

            String result = doSearch(query, count, freshness, apiKey);

            log.info("MCP 工具调用完成, toolId={}, query={}, count={}, elapsed={}ms",
                    TOOL_ID, query, count, System.currentTimeMillis() - startMs);
            return McpToolResults.success(result);
        } catch (Exception e) {
            log.error("MCP 工具调用失败, toolId={}, elapsed={}ms",
                    TOOL_ID, System.currentTimeMillis() - startMs, e);
            return McpToolResults.failure("Search", e);
        }
    }

    /**
     * Call the You.com Search API and format the result text
     */
    private String doSearch(String query, int count, String freshness, String apiKey) throws Exception {
        StringBuilder url = new StringBuilder(apiUrl)
                .append("?query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8))
                .append("&count=").append(count);
        if (StrUtil.isNotBlank(freshness)) {
            url.append("&freshness=").append(freshness);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(10))
                .header("X-API-Key", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            // Do not echo the response body, to avoid leaking account data; 401 auth failure / 429 rate limit / 5xx server error
            throw new McpToolException("You.com API returned an unexpected status: " + response.statusCode());
        }

        return formatResults(objectMapper.readTree(response.body()), count);
    }

    /**
     * Format the response as numbered title / link / excerpt text
     * <p>
     * results.web and results.news may both be missing;
     * every field other than url, title, description, and snippets is optional and is read defensively
     * <p>
     * You.com's count means "per section" (at most count web items and count news items). After merging, both are truncated to count,
     * so count means "upper bound on the total number of results", which matches intuition and keeps extra results from spending LLM tokens
     */
    private String formatResults(JsonNode root, int count) {
        JsonNode results = root.path("results");
        List<JsonNode> items = new ArrayList<>();
        collectItems(items, results.path("web"));
        collectItems(items, results.path("news"));

        if (items.isEmpty()) {
            return "未检索到相关结果，请尝试更换关键词。";
        }
        if (items.size() > count) {
            items = items.subList(0, count);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("检索完成，共 %d 条结果：\n\n", items.size()));
        int index = 1;
        for (JsonNode item : items) {
            String title = item.path("title").asText("(无标题)");
            String url = item.path("url").asText("");
            String excerpt = resolveExcerpt(item);

            sb.append(String.format("%d. %s\n", index++, title));
            if (!url.isBlank()) {
                sb.append("   链接: ").append(url).append('\n');
            }
            if (!excerpt.isBlank()) {
                sb.append("   摘录: ").append(excerpt).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * Prefer description for the excerpt. Fall back to the first snippet when it is missing
     */
    private String resolveExcerpt(JsonNode item) {
        String description = item.path("description").asText("");
        if (!description.isBlank()) {
            return description;
        }
        JsonNode snippets = item.path("snippets");
        if (snippets.isArray() && !snippets.isEmpty()) {
            return snippets.get(0).asText("");
        }
        return "";
    }

    private void collectItems(List<JsonNode> items, JsonNode array) {
        if (array != null && array.isArray()) {
            array.forEach(items::add);
        }
    }

    /**
     * Read an environment variable (testability: unit tests can override this and hide the real environment)
     */
    protected String readEnv(String name) {
        return System.getenv(name);
    }
}
