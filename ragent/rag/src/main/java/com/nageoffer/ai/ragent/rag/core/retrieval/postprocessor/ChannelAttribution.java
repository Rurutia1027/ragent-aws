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

package com.nageoffer.ai.ragent.rag.core.retrieval.postprocessor;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunkKey;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchChannelType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retrieval-channel attribution helper
 * <p>
 * {@link RetrievedChunk} carries no source-channel field (the framework DTO stays pure), so each piece of evidence is mapped back from an immutable
 * {@link SearchChannelResult} by chunk key to the channels that produced it. Fusion and rerank use that for "per-channel contribution / survival" logs
 * <p>
 * The attribution key always comes from {@link RetrievedChunkKey}, so channel dedup, fusion, and attribution do not use different identity rules
 */
final class ChannelAttribution {

    private ChannelAttribution() {
    }

    /**
     * Channels that hit each chunk key (one piece of evidence can be hit by several paths, so the value is a set)
     */
    static Map<String, Set<SearchChannelType>> index(List<SearchChannelResult> results) {
        Map<String, Set<SearchChannelType>> index = new HashMap<>();
        if (results == null) {
            return index;
        }
        for (SearchChannelResult result : results) {
            if (result == null || result.getChunks() == null) {
                continue;
            }
            for (RetrievedChunk chunk : result.getChunks()) {
                index.computeIfAbsent(RetrievedChunkKey.of(chunk), k -> EnumSet.noneOf(SearchChannelType.class))
                        .add(result.getChannelType());
            }
        }
        return index;
    }

    /**
     * Distribution of the given chunks by channel (a multi-path hit is counted once per hit channel), for example {intent-directed=4, graph=8}
     */
    static Map<SearchChannelType, Integer> countByChannel(List<RetrievedChunk> chunks,
                                                          Map<String, Set<SearchChannelType>> index) {
        Map<SearchChannelType, Integer> counts = new EnumMap<>(SearchChannelType.class);
        for (RetrievedChunk chunk : chunks) {
            Set<SearchChannelType> channels = index.get(RetrievedChunkKey.of(chunk));
            if (channels == null) {
                continue;
            }
            for (SearchChannelType channel : channels) {
                counts.merge(channel, 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Count of chunks that hit the given channel, for before/after comparisons such as "graph survival rate"
     */
    static long countOfChannel(List<RetrievedChunk> chunks,
                               Map<String, Set<SearchChannelType>> index,
                               SearchChannelType channel) {
        return chunks.stream()
                .map(RetrievedChunkKey::of)
                .map(index::get)
                .filter(set -> set != null && set.contains(channel))
                .count();
    }

    /**
     * Channel distribution as a readable string, for example "intent-directed=4 graph=8 keyword=6"
     */
    static String format(Map<SearchChannelType, Integer> counts) {
        if (counts.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        counts.forEach((type, n) -> sb.append(label(type)).append('=').append(n).append(' '));
        return sb.toString().trim();
    }

    /**
     * Readable label for a channel type
     */
    static String label(SearchChannelType type) {
        return switch (type) {
            case VECTOR -> "向量";
            case KEYWORD -> "关键词";
            case GRAPH -> "图谱";
            case WEB_SEARCH -> "联网";
            case HYBRID -> "混合";
        };
    }
}
