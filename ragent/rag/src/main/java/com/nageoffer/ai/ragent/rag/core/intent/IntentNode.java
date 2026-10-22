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

package com.nageoffer.ai.ragent.rag.core.intent;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import com.nageoffer.ai.ragent.rag.enums.IntentLevel;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Data
@Builder
public class IntentNode {

    /**
     * Unique id, for example:
     * - "group" / "group-hr" / "biz-oa-intro" / "middleware-redis"
     */
    private String id;

    /**
     * Knowledge-base id.
     */
    private String kbId;

    /**
     * Display name, for example "HR", "OA system", "data security".
     */
    private String name;

    /**
     * Semantic description, used as the embedding hint.
     */
    private String description;

    /**
     * Level: DOMAIN / CATEGORY / TOPIC.
     */
    private IntentLevel level;

    /**
     * Parent node id; null for a root.
     */
    private String parentId;

    /**
     * Example questions. Leaf nodes in particular can carry typical phrasings so the embedding model aligns more precisely.
     */
    @Builder.Default
    private List<String> examples = new ArrayList<>();

    /**
     * Child nodes. No children means a leaf.
     */
    @Builder.Default
    private List<IntentNode> children = new ArrayList<>();

    /**
     * Full path for debugging and logging only, for example "group IT > HR".
     */
    @Builder.Default
    private String fullPath = "";

    /**
     * Whether this node is a knowledge base or a system interaction.
     */
    @Builder.Default
    private IntentKind kind = IntentKind.KB;

    /**
     * Milvus collection name (meaningful only when kind=KB).
     * Kept only for old caches and old data.
     */
    private String collectionName;

    /**
     * One KB intent may bind several logical collections.
     */
    @Builder.Default
    private List<String> collectionNames = new ArrayList<>();

    /**
     * MCP tool id (meaningful only when kind=MCP).
     */
    private String mcpToolId;

    /**
     * Whether the user must confirm before the tool runs (meaningful only when kind=MCP).
     */
    private boolean requireConfirm;

    /**
     * Node-level retrieval topK (optional).
     * Falls back to the global topK when unset.
     */
    private Integer topK;

    /**
     * Short rule snippet (optional).
     */
    private String promptSnippet;

    /**
     * Full prompt template for this scene (optional).
     */
    private String promptTemplate;

    /**
     * Whether this is a final (leaf) node:
     * - only leaves attach a knowledge base (Milvus collection)
     * - only leaves take part in intent matching scores
     */
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    /**
     * Whether this is a KB node.
     */
    public boolean isKB() {
        return kind == null || kind == IntentKind.KB;
    }

    /**
     * Whether this is an MCP node.
     */
    public boolean isMCP() {
        return kind == IntentKind.MCP;
    }

    /**
     * Whether this is a SYSTEM node.
     */
    public boolean isSystem() {
        return kind == IntentKind.SYSTEM;
    }

    /**
     * Collections this intent actually searches.
     * The new field wins; the old single-collection field is only a smooth-upgrade fallback.
     */
    @JsonIgnore
    public List<String> getEffectiveCollectionNames() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (collectionNames != null) {
            collectionNames.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(normalized::add);
        }
        if (normalized.isEmpty() && StrUtil.isNotBlank(collectionName)) {
            normalized.add(collectionName.trim());
        }
        return List.copyOf(normalized);
    }
}
