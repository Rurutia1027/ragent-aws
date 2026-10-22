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
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.rag.controller.vo.GraphViewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Knowledge-graph visualization query service.
 * <p>
 * Admin visualization entry: fetch the graph through {@link LightRagClient} and map it to the frontend view {@link GraphViewVO}.
 * When the graph channel is disabled (rag.graph.type=none) there is no LightRagClient bean, so this throws a business exception; the route itself always exists.
 * <p>
 * Depends only on LightRAG's normalized graph semantics, not on a direct Neo4j connection: the backend needs no graph-database driver or connection config, and swapping storage does not affect this layer.
 */
@Service
@RequiredArgsConstructor
public class GraphQueryService {

    private final ObjectProvider<LightRagClient> lightRagClientProvider;

    /**
     * Query a graph subgraph.
     *
     * @param entity     start entity name; blank means the whole graph
     * @param collection knowledge-base collectionName; limits the view to that collection's subgraph, blank means no limit
     * @param doc        document id; limits the view to that document's subgraph, takes priority over collection, blank means no limit
     * @param depth      subgraph depth; non-positive values use the default of 2
     * @param limit      node cap; non-positive values use the default of 200, hard cap 1000
     */
    public GraphViewVO getGraph(String entity, String collection, String doc, int depth, int limit) {
        LightRagClient client = requireClient();
        int maxDepth = depth > 0 ? depth : 2;
        int maxNodes = limit > 0 ? Math.min(limit, 1000) : 200;
        String label = StrUtil.isNotBlank(entity) ? entity : "*";
        // Scope-filter token: prefer the finest grain, the document (docId is a unique snowflake); otherwise the knowledge-base {collectionName}_ prefix.
        // Same convention as LightRagClient.deleteByCollection/deleteByDoc; it matches the source carried in node properties.file_path.
        String token = null;
        if (StrUtil.isNotBlank(doc)) {
            token = doc;
        } else if (StrUtil.isNotBlank(collection)) {
            token = collection + "_";
        }
        // When a scope filter is set, ask LightRAG for the server-side maximum so enough nodes remain after the file_path filter.
        int fetchNodes = token != null ? 1000 : maxNodes;
        return mapGraph(client.fetchGraph(label, maxDepth, fetchNodes), token, maxNodes);
    }

    /**
     * Search entity labels for the visualization search box; a blank keyword returns popular labels.
     */
    public List<String> searchEntities(String keyword, int limit) {
        return requireClient().fetchLabels(keyword, limit);
    }

    private LightRagClient requireClient() {
        LightRagClient client = lightRagClientProvider.getIfAvailable();
        if (client == null) {
            throw new ServiceException("Knowledge graph channel is disabled (rag.graph.type=none)");
        }
        return client;
    }

    /**
     * Map LightRAG's raw {nodes,edges,is_truncated} payload to the frontend view.
     * <p>
     * Node display name is properties.entity_id, then labels[0], then the internal id; type and description come from the matching properties fields.
     * Edge label is properties.keywords, then type; relation description is properties.description. Edges missing an id fall back to source-target. Reads are defensive.
     * <p>
     * When token is non-null, keep only nodes whose properties.file_path contains the token, and drop dangling edges whose endpoints are not both kept.
     * If the filtered set still exceeds limit, truncate to limit and set truncated. file_path is used only for internal filtering and is not copied into the VO.
     *
     * @param token source-filter token; null means no filter
     * @param limit display node cap
     */
    private GraphViewVO mapGraph(JsonNode root, String token, int limit) {
        List<GraphViewVO.Node> nodes = new ArrayList<>();
        List<GraphViewVO.Edge> edges = new ArrayList<>();
        boolean truncated = false;
        if (root != null) {
            truncated = root.path("is_truncated").asBoolean(false);
            Set<String> keptIds = new HashSet<>();
            JsonNode nodeArray = root.path("nodes");
            if (nodeArray.isArray()) {
                for (JsonNode node : nodeArray) {
                    String id = node.path("id").asText("");
                    if (StrUtil.isBlank(id)) {
                        continue;
                    }
                    JsonNode props = node.path("properties");
                    // Scope filter: drop the node when token is set and its source file_path does not contain the token.
                    if (token != null && !props.path("file_path").asText("").contains(token)) {
                        continue;
                    }
                    // Truncate to the display cap after filtering: at the cap, mark truncated and stop collecting nodes (LightRAG already sorts by hop count plus degree, so the first limit nodes are the most relevant).
                    if (nodes.size() >= limit) {
                        truncated = true;
                        break;
                    }
                    String name = props.path("entity_id").asText("");
                    if (StrUtil.isBlank(name)) {
                        JsonNode labels = node.path("labels");
                        if (labels.isArray() && !labels.isEmpty()) {
                            name = labels.get(0).asText("");
                        }
                    }
                    keptIds.add(id);
                    nodes.add(GraphViewVO.Node.builder()
                            .id(id)
                            .name(StrUtil.isNotBlank(name) ? name : id)
                            .type(props.path("entity_type").asText(""))
                            .description(cleanMerged(props.path("description").asText(""), "\n"))
                            .build());
                }
            }
            JsonNode edgeArray = root.path("edges");
            if (edgeArray.isArray()) {
                for (JsonNode edge : edgeArray) {
                    String source = edge.path("source").asText("");
                    String target = edge.path("target").asText("");
                    if (StrUtil.isBlank(source) || StrUtil.isBlank(target)) {
                        continue;
                    }
                    // Keep the edge only when both endpoints are in the kept set, dropping dangling edges created by filtering or truncation.
                    if (!keptIds.contains(source) || !keptIds.contains(target)) {
                        continue;
                    }
                    JsonNode props = edge.path("properties");
                    // Keywords are also a multi-source merge; split on <SEP>, dedupe, and join inline with slashes.
                    String label = cleanMerged(props.path("keywords").asText(""), " / ");
                    if (StrUtil.isBlank(label)) {
                        label = edge.path("type").asText("");
                    }
                    edges.add(GraphViewVO.Edge.builder()
                            .id(edge.path("id").asText(source + "-" + target))
                            .source(source)
                            .target(target)
                            .label(label)
                            .description(cleanMerged(props.path("description").asText(""), "\n"))
                            .build());
                }
            }
        }
        return GraphViewVO.builder().nodes(nodes).edges(edges).truncated(truncated).build();
    }

    /**
     * Normalize a LightRAG multi-source merged string.
     * <p>
     * {@code <SEP>} is LightRAG's internal separator when merging descriptions or keywords of the same entity or relation across sources; passing it through to the frontend is unfriendly.
     * Split on it, trim and dedupe each segment, then rejoin with joiner: descriptions use newlines so each source is its own line, keywords use slashes inline.
     *
     * @param raw    raw LightRAG string, which may contain zero or more {@code <SEP>} separators
     * @param joiner joiner used to reassemble the deduped segments
     */
    private static String cleanMerged(String raw, String joiner) {
        if (StrUtil.isBlank(raw)) {
            return "";
        }
        List<String> seen = new ArrayList<>();
        for (String part : raw.split("<SEP>")) {
            String piece = part.trim();
            if (StrUtil.isNotBlank(piece) && !seen.contains(piece)) {
                seen.add(piece);
            }
        }
        return String.join(joiner, seen);
    }
}
