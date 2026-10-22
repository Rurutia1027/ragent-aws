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

package com.nageoffer.ai.ragent.core.chunk.blockaware;

import com.nageoffer.ai.ragent.core.chunk.model.ChunkDraft;
import com.nageoffer.ai.ragent.core.parser.model.Block;

import java.util.List;

/**
 * Per-Block-type splitter. Each implementation declares which Block type it handles and how it cuts
 * <p>
 * That lets the dispatcher work by lookup: a new Block type needs only one new implementation. Whether a block
 * can merge with its neighbors is not decided by Block type; {@link ChunkPacker} computes it from the budget
 *
 * @param <B> Block subtype this chunker handles
 */
public interface BlockChunker<B extends Block> {

    /**
     * Registration key: the Block type this chunker handles
     */
    Class<B> blockType();

    /**
     * Splits one Block into drafts, which may be empty. Indexes and chunk ids are assigned together at assembly
     * <p>
     * The cut-or-not rule is the same for every type: if the whole block fits in
     * {@link com.nageoffer.ai.ragent.core.chunk.model.ChunkBudget#toleranceChars()}, do not cut. Cut at chunk size
     * only when it exceeds that, and every cut falls on a structural boundary (line, table row, list item, sentence end)
     */
    List<ChunkDraft> chunk(B block, ChunkContext ctx);
}
