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
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrievalBudget;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchChannelType;
import com.nageoffer.ai.ragent.rag.core.retrieval.channel.SearchContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for the dedup processor
 * <p>
 * Covers two historical defects:
 * 1. A chunk with no id used String.hashCode() as the dedup key. A hash collision (for example "Aa" and "BB")
 *    treated chunks with different content as duplicates and dropped one silently
 * 2. A null score unboxed during the same-key comparison and threw NPE, so the whole dedup processor failed
 */
class DeduplicationPostProcessorTest {

    private RetrievedChunk chunk(String id, String text, Float score) {
        RetrievedChunk c = new RetrievedChunk();
        c.setId(id);
        c.setText(text);
        c.setScore(score);
        return c;
    }

    private List<SearchChannelResult> singleChannel(List<RetrievedChunk> chunks) {
        return List.of(SearchChannelResult.builder()
                .channelType(SearchChannelType.KEYWORD)
                .channelName("Keyword")
                .chunks(chunks)
                .latencyMs(1)
                .build());
    }

    @Test
    void hashCollidingTextsAreNotDeduplicated() {
        // "Aa" and "BB" have the same String.hashCode() (both 2112), but different content, so they must not be merged
        assertEquals("Aa".hashCode(), "BB".hashCode(), "前置条件：两段文本哈希碰撞");
        List<RetrievedChunk> chunks = new ArrayList<>();
        chunks.add(chunk(null, "Aa", 0.9f));
        chunks.add(chunk(null, "BB", 0.8f));

        List<RetrievedChunk> out = new DeduplicationPostProcessor()
                .process(chunks, singleChannel(chunks), SearchContext.builder().originalQuestion("q").budget(RetrievalBudget.uniform(10)).build());

        assertEquals(2, out.size(), "哈希碰撞的不同内容 Chunk 不允许被去重合并");
    }

    @Test
    void nullScoreDedupKeepsFirstOccurrence() {
        List<RetrievedChunk> chunks = new ArrayList<>();
        chunks.add(chunk("same-id", "文本A", null));
        chunks.add(chunk("same-id", "文本B", 0.5f));

        assertDoesNotThrow(() -> {
            List<RetrievedChunk> out = new DeduplicationPostProcessor()
                    .process(chunks, singleChannel(chunks), SearchContext.builder().originalQuestion("q").budget(RetrievalBudget.uniform(10)).build());
            assertEquals(1, out.size(), "同 id 应去重为 1 条");
            // Dedup by key keeps the first instance and does not compare scores (cross-channel scales are not comparable; final rank is left to downstream RRF). A null score must not throw
            assertEquals("文本A", out.get(0).getText(), "应保留首次出现的实例");
        });
    }
}
