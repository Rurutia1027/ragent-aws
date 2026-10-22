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

package com.nageoffer.ai.ragent.rag.core.graph;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.config.GraphProperties;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * HTTP client for the LightRAG microservice.
 * <p>
 * Wraps calls to the LightRAG server (default :9621): retrieval for context, and document writes.
 * Registered only when rag.graph.type=lightrag. Any call failure degrades (retrieval returns empty, writes log a warn) and never blocks the main path.
 * <p>
 * Important: LightRAG /query has no per-request workspace parameter — workspace is instance-level (fixed by the server env).
 * One instance is therefore one graph, and KB ownership can only be decided on the result side by file_path (see retrieveByScope). True subgraph isolation needs multiple instances and is a later phase.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rag.graph", name = "type", havingValue = "lightrag")
public class LightRagClient {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GraphProperties properties;
    private final SearchChannelProperties searchProperties;

    public LightRagClient(@Qualifier("syncHttpClient") OkHttpClient httpClient,
                          ObjectMapper objectMapper,
                          GraphProperties properties,
                          SearchChannelProperties searchProperties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.searchProperties = searchProperties;
    }

    /**
     * Retrieve graph context and split evidence by collections into "matched collection" and "unmatched collection".
     * <p>
     * only_need_context=true fetches evidence only and does not let LightRAG generate an answer; evidence returns to the main path on equal footing with other channels.
     * One query is the whole graph, and ownership is decided on the result side, so the split adds no extra call.
     * The caller then assigns a quota to each side, so a wrong intent does not drop the unmatched-collection evidence entirely.
     *
     * @param question    query question
     * @param mode        LightRAG query mode: naive / local / global / hybrid / mix
     * @param topK        desired candidate count
     * @param collections target knowledge-base collection names; empty puts everything in the matched side
     */
    public GraphEvidence retrieveByScope(String question, String mode, int topK, Collection<String> collections) {
        if (StrUtil.isBlank(question)) {
            return GraphEvidence.empty();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("query", question);
            body.put("mode", StrUtil.isNotBlank(mode) ? mode : "mix");
            body.put("only_need_context", true);
            body.put("include_references", true);
            body.put("include_chunk_content", true);
            if (topK > 0) {
                body.put("top_k", topK);
            }
            JsonNode root = postQuery(body);
            return root != null ? parseReferences(root, collections) : GraphEvidence.empty();
        } catch (Exception e) {
            log.warn("LightRAG 检索失败，降级为空结果: {}", e.getMessage());
            return GraphEvidence.empty();
        }
    }

    /**
     * Fetch a graph subgraph for admin visualization (read-only, independent of retrieve's evidence recall).
     * <p>
     * label is the start entity name; "*" fetches the whole graph. The server truncates at maxNodes by priority of hop count to the start plus node degree,
     * and returns the raw {@code {nodes,edges,is_truncated}} structure for the caller to map into a frontend view. Failure degrades to null.
     *
     * @param label    start entity name; "*" means the whole graph
     * @param maxDepth maximum subgraph depth
     * @param maxNodes maximum node count (server cap 1000)
     */
    public JsonNode fetchGraph(String label, int maxDepth, int maxNodes) {
        try {
            HttpUrl base = HttpUrl.parse(url("/graphs"));
            if (base == null) {
                return null;
            }
            HttpUrl target = base.newBuilder()
                    .addQueryParameter("label", StrUtil.isNotBlank(label) ? label : "*")
                    .addQueryParameter("max_depth", String.valueOf(Math.max(1, maxDepth)))
                    .addQueryParameter("max_nodes", String.valueOf(Math.max(1, maxNodes)))
                    .build();
            return execute(auth(new Request.Builder().url(target).get()), "/graphs");
        } catch (Exception e) {
            log.warn("LightRAG 图谱拉取失败 label={}: {}", label, e.getMessage());
            return null;
        }
    }

    /**
     * Search entity labels for the visualization entity search box.
     * <p>
     * A blank keyword returns popular labels (the default entry into the graph); otherwise fuzzy-search by keyword. Defensively accepts either plain strings or object elements.
     *
     * @param keyword keyword; blank returns popular labels
     * @param limit   result cap
     */
    public List<String> fetchLabels(String keyword, int limit) {
        try {
            boolean popular = StrUtil.isBlank(keyword);
            HttpUrl base = HttpUrl.parse(url(popular ? "/graph/label/popular" : "/graph/label/search"));
            if (base == null) {
                return List.of();
            }
            HttpUrl.Builder builder = base.newBuilder();
            if (popular) {
                builder.addQueryParameter("limit", String.valueOf(clamp(limit, 300, 1000)));
            } else {
                builder.addQueryParameter("q", keyword)
                        .addQueryParameter("limit", String.valueOf(clamp(limit, 50, 100)));
            }
            JsonNode root = execute(auth(new Request.Builder().url(builder.build()).get()), "labels");
            List<String> labels = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode node : root) {
                    String value = node.isTextual()
                            ? node.asText("")
                            : node.path("label").asText(node.path("name").asText(""));
                    if (StrUtil.isNotBlank(value)) {
                        labels.add(value);
                    }
                }
            }
            return labels;
        } catch (Exception e) {
            log.warn("LightRAG 标签检索失败 keyword={}: {}", keyword, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fallback and cap: non-positive values fall back to fallback, values above max are clamped to max.
     */
    private int clamp(int value, int fallback, int max) {
        int v = value > 0 ? value : fallback;
        return Math.min(v, max);
    }

    /**
     * Write or update one document into the graph.
     * <p>
     * file_source encodes the source id (for example docId) so retrieval can trace document ownership from file_path.
     *
     * @param text       full document text
     * @param fileSource source id (prefer docId)
     */
    public void insertText(String text, String fileSource) {
        if (StrUtil.isBlank(text)) {
            return;
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("text", text);
            if (StrUtil.isNotBlank(fileSource)) {
                body.put("file_source", fileSource);
            }
            post("/documents/text", body);
        } catch (Exception e) {
            log.warn("LightRAG 文档写入失败 file_source={}: {}", fileSource, e.getMessage());
        }
    }

    /**
     * Delete one document's graph data (match file_path by docId).
     * <p>
     * docId is globally unique (snowflake) and sits in file_path as a token, so matching stays stable under the server's basename normalization.
     */
    public void deleteByDoc(String docId) {
        if (StrUtil.isBlank(docId)) {
            return;
        }
        deleteMatching(filePath -> filePath.contains(docId), "docId=" + docId);
    }

    /**
     * Delete all graph data for one knowledge base.
     * <p>
     * Match by equality on the collection name parsed by {@link GraphFileSource#parse}: names may be prefixes of each other (kb and kb_hr may legally coexist),
     * and a substring match would wipe another collection's graph data irreversibly.
     */
    public void deleteByCollection(String collectionName) {
        if (StrUtil.isBlank(collectionName)) {
            return;
        }
        deleteMatching(filePath -> {
            GraphFileSource source = GraphFileSource.parse(filePath);
            return source != null && collectionName.equals(source.collectionName());
        }, "collection=" + collectionName);
    }

    /**
     * List documents, match LightRAG doc_ids by a file_path predicate, then delete them in batch.
     * <p>
     * LightRAG deletes by its internal doc_id (content-derived), so look them up with GET /documents, then DELETE /documents/delete_document.
     * The full list is matched in memory; a very large corpus can switch to /documents/paginated filtering. Best-effort: any step that fails only logs a warn.
     */
    private void deleteMatching(Predicate<String> filePathMatch, String logKey) {
        try {
            JsonNode docs = get("/documents");
            if (docs == null) {
                return;
            }
            List<String> docIds = new ArrayList<>();
            JsonNode statuses = docs.path("statuses");
            if (statuses.isObject()) {
                statuses.forEach(group -> {
                    if (group.isArray()) {
                        for (JsonNode d : group) {
                            String filePath = d.path("file_path").asText("");
                            if (StrUtil.isNotBlank(filePath) && filePathMatch.test(filePath)) {
                                String id = d.path("id").asText("");
                                if (StrUtil.isNotBlank(id)) {
                                    docIds.add(id);
                                }
                            }
                        }
                    }
                });
            }
            if (docIds.isEmpty()) {
                return;
            }
            ObjectNode body = objectMapper.createObjectNode();
            body.set("doc_ids", objectMapper.valueToTree(docIds));
            delete("/documents/delete_document", body);
        } catch (Exception e) {
            log.warn("LightRAG 文档删除失败 {}: {}", logKey, e.getMessage());
        }
    }

    /**
     * /query only: timeout comes from the channel budget rag.search.channels.timeout-ms. Once the channel drops the result, the client has no reason to keep waiting.
     * <=0 sets no extra timeout. Only retrieval uses this path; writes, deletes, and visualization keep the base client timeout and are unaffected by the retrieval budget.
     */
    private JsonNode postQuery(JsonNode body) throws Exception {
        long timeoutMs = searchProperties.getChannels().getTimeoutMs();
        OkHttpClient client = timeoutMs <= 0 ? httpClient : httpClient.newBuilder()
                .callTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .build();
        return execute(auth(new Request.Builder().url(url("/query"))
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))), "/query", client);
    }

    private JsonNode post(String path, JsonNode body) throws Exception {
        return execute(auth(new Request.Builder().url(url(path))
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))), path);
    }

    private JsonNode get(String path) throws Exception {
        return execute(auth(new Request.Builder().url(url(path)).get()), path);
    }

    private JsonNode delete(String path, JsonNode body) throws Exception {
        return execute(auth(new Request.Builder().url(url(path))
                .delete(RequestBody.create(objectMapper.writeValueAsString(body), JSON))), path);
    }

    private String url(String path) {
        return StrUtil.removeSuffix(properties.getLightrag().getBaseUrl(), "/") + path;
    }

    /**
     * Local deployments have no key by default; attach the auth header only when one is explicitly configured.
     */
    private Request.Builder auth(Request.Builder builder) {
        String apiKey = properties.getLightrag().getApiKey();
        if (StrUtil.isNotBlank(apiKey)) {
            builder.header("X-API-Key", apiKey);
        }
        return builder;
    }

    /**
     * Shared execute path: non-2xx or an empty response returns null; exceptions propagate so the caller can degrade.
     */
    private JsonNode execute(Request.Builder builder, String path) throws Exception {
        return execute(builder, path, httpClient);
    }

    private JsonNode execute(Request.Builder builder, String path, OkHttpClient client) throws Exception {
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                log.warn("LightRAG 请求失败 path={}, code={}", path, response.code());
                return null;
            }
            String bodyStr = response.body() != null ? response.body().string() : "";
            return StrUtil.isNotBlank(bodyStr) ? objectMapper.readTree(bodyStr) : null;
        }
    }

    /**
     * Parse /query response references into RetrievedChunk.
     * <p>
     * Shape {@code {"response":"...","references":[{"reference_id","file_path","content":[...]}]}}.
     * When references are missing or empty, fall back to treating the whole response context as one evidence chunk. Reads are defensive.
     * <p>
     * When collections is non-empty, split by file_path ownership into two lists. The response fallback chunk has no file_path and cannot be assigned, so it is skipped when the split is active.
     * <p>
     * Both lists share one global rank counter: rank is the relevance order LightRAG assigned on the whole graph.
     * If each list started at 0, a strong unmatched hit would score the same as a weak matched hit and erase the graph's own judgment.
     */
    private GraphEvidence parseReferences(JsonNode root, Collection<String> collections) {
        boolean filterByCollection = collections != null && !collections.isEmpty();
        List<RetrievedChunk> chunks = new ArrayList<>();
        List<RetrievedChunk> unmatched = new ArrayList<>();
        JsonNode references = root.path("references");
        if (references.isArray() && !references.isEmpty()) {
            int rank = 0;
            for (JsonNode ref : references) {
                String refId = ref.path("reference_id").asText("");
                String filePath = ref.path("file_path").asText("");
                // Split on the result side by matched collection: each list stays ordered, and rank comes from the whole-graph sequence.
                boolean matched = !filterByCollection || matchesCollection(filePath, collections);
                StringBuilder text = new StringBuilder();
                JsonNode content = ref.path("content");
                if (content.isArray()) {
                    for (JsonNode c : content) {
                        String s = c.asText("");
                        if (StrUtil.isNotBlank(s)) {
                            text.append(s).append('\n');
                        }
                    }
                }
                String body = text.toString().trim();
                if (StrUtil.isBlank(body)) {
                    body = filePath;
                }
                if (StrUtil.isBlank(body)) {
                    continue;
                }
                // Parse owning collection and docId from file_path so graph evidence lines up with vector evidence at both collection and document level:
                // collection lets downstream derive intent ownership by collection; docId lets the final enrichment fill the real title and group it with same-source vector evidence.
                GraphFileSource source = GraphFileSource.parse(filePath);
                String docId = source != null ? source.docId() : null;
                // Score is the rank-decaying neutral value 1/(rank+1) over the whole graph: dimensionless, it only expresses relative order from the graph's view.
                // With multiple channels FusionPostProcessor (RRF) recomputes it; when rerank is on, the rerank model overwrites it.
                (matched ? chunks : unmatched).add(RetrievedChunk.builder()
                        .id(StrUtil.isNotBlank(refId) ? refId : "graph:" + rank)
                        .text(body)
                        .score(1.0f / (rank + 1))
                        .collectionName(source != null ? source.collectionName() : null)
                        .docId(docId)
                        // When docId parses, leave docName blank and let enrichment fill the real title from docId; fall back to file_path only when it does not parse, so the source is not entirely missing.
                        .docName(docId != null ? null : (StrUtil.isNotBlank(filePath) ? filePath : null))
                        .build());
                rank++;
            }
            return new GraphEvidence(chunks, unmatched);
        }
        // Fallback: when references are missing, use the response context as a single evidence chunk.
        // The fallback chunk has no file_path and cannot be assigned, so drop it when filtering is active. Warn because this usually means the server does not support references.
        String context = root.path("response").asText("");
        if (StrUtil.isNotBlank(context)) {
            if (filterByCollection) {
                log.warn("LightRAG 未返回 references，兜底上下文无法按库归属已丢弃，请检查服务端版本与 include_references 支持");
            } else {
                chunks.add(RetrievedChunk.builder()
                        .id("graph:context")
                        .text(context)
                        .score(1.0f)
                        .build());
            }
        }
        return new GraphEvidence(chunks, unmatched);
    }

    /**
     * Whether file_path belongs to any of the given collections.
     * <p>
     * Compare the parsed full name by equality, the same rule as deleteByCollection. A parse failure counts as not belonging,
     * so the evidence falls into the supplement side rather than the primary side, which is safer than a wrong assignment.
     */
    private boolean matchesCollection(String filePath, Collection<String> collections) {
        GraphFileSource source = GraphFileSource.parse(filePath);
        return source != null && collections.contains(source.collectionName());
    }
}
