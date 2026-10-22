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

package com.nageoffer.ai.ragent.rag.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Knowledge-graph visualization view
 * <p>
 * Shaped as {nodes, edges} for the frontend, decoupled from the underlying graph store (Neo4j and others):
 * the backend only knows LightRAG's normalized graph semantics, so changing the store does not affect the frontend
 */
@Data
@Builder
public class GraphViewVO {

    /**
     * Nodes
     */
    private List<Node> nodes;

    /**
     * Edges
     */
    private List<Edge> edges;

    /**
     * Whether the result was truncated by the node cap (so the frontend can say the graph is too large and only part is shown)
     */
    private boolean truncated;

    /**
     * Graph node
     */
    @Data
    @Builder
    public static class Node {

        /**
         * Unique node id. Edges refer to it
         */
        private String id;

        /**
         * Display name (entity name)
         */
        private String name;

        /**
         * Entity type, so the frontend can color by type
         */
        private String type;

        /**
         * Entity description, for the hover detail
         */
        private String description;
    }

    /**
     * Graph edge
     */
    @Data
    @Builder
    public static class Edge {

        /**
         * Unique edge id
         */
        private String id;

        /**
         * Source node id
         */
        private String source;

        /**
         * Target node id
         */
        private String target;

        /**
         * Relation label (keyword / type)
         */
        private String label;

        /**
         * Relation description, shown on hover as a readable explanation of the relation
         */
        private String description;
    }
}
