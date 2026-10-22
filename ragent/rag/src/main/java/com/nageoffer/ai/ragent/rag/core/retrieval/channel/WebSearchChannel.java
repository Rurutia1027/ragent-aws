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

package com.nageoffer.ai.ragent.rag.core.retrieval.channel;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * You.com web retrieval channel
 * <p>
 * Live web recall from the You.com Search API, complementary to local knowledge-base channels:
 * strong on time-sensitive questions and public news that the local knowledge base does not cover
 * <p>
 * Enablement (all required):
 * - rag.search.channels.web-search.enabled = true
 * - an API key can be resolved (configured api-key first; if empty, fall back to the YDC_API_KEY environment variable)
 * <p>
 * An optional external channel that runs in parallel with the local channels. Results enter RRF fusion together. Channels have no order or priority.
 * Any failure (network error, non-2xx, malformed response, timeout) logs a warning and returns an empty result.
 * A web-search failure must never affect the local retrieval path
 * <p>
 * Note: mcp-server's {@code YouComSearchMcpExecutor} contains a parallel You.com client on purpose — it duplicates this one.
 * mcp-server is a zero-internal-dependency service that can be deployed alone (not the same JVM as this module, and a different consumer). A shared module would break that isolation,
 * so this is treated as service-level duplication. A You.com contract change (endpoint / parameters / response shape) must be applied in both places
 */
@Slf4j
@Component
public class WebSearchChannel implements SearchChannel {

    /**
     * API key environment variable name (team convention; do not change)
     */
    private static final String ENV_API_KEY = "YDC_API_KEY";

    /**
     * Upper bound on results returned by one search
     */
    private static final int MAX_COUNT = 20;

    /**
     * Default result count (fallback when the config is invalid)
     */
    private static final int DEFAULT_COUNT = 5;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SearchChannelProperties properties;

    public WebSearchChannel(@Qualifier("syncHttpClient") OkHttpClient httpClient,
                                  ObjectMapper objectMapper,
                                  SearchChannelProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String getName() {
        return "YouComWebSearch";
    }

    @Override
    public SearchChannelType getType() {
        return SearchChannelType.WEB_SEARCH;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        SearchChannelProperties.WebSearch config = properties.getChannels().getWebSearch();
        return config.isEnabled() && StrUtil.isNotBlank(resolveApiKey());
    }

    @Override
    public SearchChannelResult search(SearchContext context) {
        long startTime = System.currentTimeMillis();
        try {
            String query = context.getMainQuestion();
            if (StrUtil.isBlank(query)) {
                log.info("You.com 联网检索问题为空，跳过");
                return emptyResult(System.currentTimeMillis() - startTime);
            }

            SearchChannelProperties.WebSearch config = properties.getChannels().getWebSearch();
            HttpUrl httpUrl = HttpUrl.parse(config.getApiUrl());
            if (httpUrl == null) {
                log.warn("You.com 联网检索 api-url 配置非法：{}，返回空结果", config.getApiUrl());
                return emptyResult(System.currentTimeMillis() - startTime);
            }

            Request request = new Request.Builder()
                    .url(httpUrl.newBuilder()
                            .addQueryParameter("query", query)
                            .addQueryParameter("count", String.valueOf(resolveCount(config)))
                            .build())
                    .header("X-API-Key", resolveApiKey())
                    .get()
                    .build();

            // Tighten the timeout from the channel config; newBuilder reuses the connection pool and thread pool, so the cost is negligible
            int timeoutSeconds = Math.max(1, config.getTimeoutSeconds());
            OkHttpClient client = httpClient.newBuilder()
                    .callTimeout(Duration.ofSeconds(timeoutSeconds))
                    .readTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            List<RetrievedChunk> chunks;
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    // 401 auth failure / 429 rate limit / 5xx server error all degrade to an empty result (do not print the key)
                    log.warn("You.com 联网检索请求失败, code={}, 返回空结果", response.code());
                    return emptyResult(System.currentTimeMillis() - startTime);
                }
                String body = response.body() != null ? response.body().string() : "";
                chunks = parseChunks(body, resolveCount(config));
            }

            long latency = System.currentTimeMillis() - startTime;
            log.info("You.com 联网检索完成，检索到 {} 个 Chunk，耗时 {}ms", chunks.size(), latency);

            return SearchChannelResult.builder()
                    .channelType(SearchChannelType.WEB_SEARCH)
                    .channelName(getName())
                    .chunks(chunks)
                    .latencyMs(latency)
                    .build();
        } catch (Exception e) {
            // Web search is a supplement channel. No exception (including timeout and response parse failure) may propagate
            log.warn("You.com 联网检索失败，降级为空结果: {}", e.getMessage());
            return emptyResult(System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Parse a You.com response into a RetrievedChunk list
     * <p>
     * Response shape {@code {"results": {"web": [...], "news": [...]}}};
     * news may be missing. On each result, every field other than url/title/description/snippets is optional and is read defensively
     * <p>
     * You.com count is per section (web and news each return at most count items). After the two sections are merged they are
     * truncated to maxResults, so count means "upper bound on total results returned", which matches the intuitive reading
     */
    private List<RetrievedChunk> parseChunks(String body, int maxResults) throws Exception {
        JsonNode results = objectMapper.readTree(body).path("results");

        List<JsonNode> items = new ArrayList<>();
        collectItems(items, results.path("web"));
        collectItems(items, results.path("news"));

        // Initial score is a rank-decreasing neutral score 1/(rank+1): dimensionless, expressing only relative order inside the channel, not compared in scale with vector cosine / BM25
        // scores. With multiple channels, FusionPostProcessor (RRF) recomputes by each channel's rank and overwrites this score. With a single channel and rerank off,
        // it keeps this channel's recall order. With rerank on, the rerank model scores again. The dedup processor is null-safe for scores,
        // so this score is not there for that
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (JsonNode item : items) {
            RetrievedChunk chunk = toChunk(item, chunks.size());
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        return chunks.size() > maxResults ? new ArrayList<>(chunks.subList(0, maxResults)) : chunks;
    }

    /**
     * Map one result
     * <p>
     * Put title, description, snippets, and the source link into text so citation data is not lost when the prompt is assembled downstream.
     * id is the url (the natural unique key of a web result, used by the dedup processor)
     * <p>
     * {@link RetrievedChunk} docId/chunkIndex/docName exist so local-store chunks can be enriched by a lookup. A web result has no store record.
     * Enrichment finds nothing by id and skips it, leaving those fields null. Context assembly renders it as a document block with no title, and evidence is still emitted
     */
    private RetrievedChunk toChunk(JsonNode item, int rank) {
        String url = item.path("url").asText("");
        String title = item.path("title").asText("");
        String description = item.path("description").asText("");

        StringBuilder text = new StringBuilder();
        if (StrUtil.isNotBlank(title)) {
            text.append("【").append(title).append("】\n");
        }
        if (StrUtil.isNotBlank(description)) {
            text.append(description).append('\n');
        }
        JsonNode snippets = item.path("snippets");
        if (snippets.isArray()) {
            for (JsonNode snippet : snippets) {
                String s = snippet.asText("");
                if (StrUtil.isNotBlank(s)) {
                    text.append(s).append('\n');
                }
            }
        }
        if (StrUtil.isNotBlank(url)) {
            text.append("来源: ").append(url);
        }

        String content = text.toString().trim();
        if (StrUtil.isBlank(content)) {
            return null;
        }
        return RetrievedChunk.builder()
                .id(StrUtil.isNotBlank(url) ? url : null)
                .text(content)
                .score(1.0f / (rank + 1))
                .build();
    }

    private void collectItems(List<JsonNode> items, JsonNode array) {
        if (array != null && array.isArray()) {
            array.forEach(items::add);
        }
    }

    /**
     * Resolve the result count: fall back to the default when the config is invalid, and truncate when it exceeds the cap
     */
    private int resolveCount(SearchChannelProperties.WebSearch config) {
        int count = config.getCount() > 0 ? config.getCount() : DEFAULT_COUNT;
        return Math.min(count, MAX_COUNT);
    }

    /**
     * Resolve the API key: configured api-key first; if empty, fall back to the YDC_API_KEY environment variable
     */
    private String resolveApiKey() {
        String apiKey = properties.getChannels().getWebSearch().getApiKey();
        return StrUtil.isNotBlank(apiKey) ? apiKey : readEnv(ENV_API_KEY);
    }

    /**
     * Read an environment variable (testability: unit tests can override this and hide the real environment)
     */
    protected String readEnv(String name) {
        return System.getenv(name);
    }

}
