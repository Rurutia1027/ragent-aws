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

import com.nageoffer.ai.ragent.core.chunk.model.Chunk;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkAssembler;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkBudget;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkDraft;
import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.core.parser.model.HeadingBlock;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chunk dispatcher: Block type -> chunker by lookup. Startup fails if two chunkers claim the same type
 * <p>
 * A heading updates the outline path before the normal dispatch, so it receives the path including itself and
 * lands in the same section, and naturally the same chunk, as the body that follows.
 * The flow is fixed: dispatch produces drafts, pack by section, then assemble once. Assembly stays last because
 * embedding text must prepend the outline, and packing can happen only before that prefix is added
 */
@Component
public class BlockAwareChunkerDispatcher {

    private final HeadingHandler headingHandler;
    private final ChunkPacker chunkPacker;
    private final Map<Class<? extends Block>, BlockChunker<?>> registry;

    public BlockAwareChunkerDispatcher(HeadingHandler headingHandler,
                                       ChunkPacker chunkPacker,
                                       List<BlockChunker<?>> chunkers) {
        this.headingHandler = headingHandler;
        this.chunkPacker = chunkPacker;
        Map<Class<? extends Block>, BlockChunker<?>> table = new HashMap<>();
        for (BlockChunker<?> chunker : chunkers) {
            BlockChunker<?> previous = table.put(chunker.blockType(), chunker);
            if (previous != null) {
                throw new ServiceException(String.format(
                        "Block chunker registration conflict: type=%s is claimed by both %s and %s",
                        chunker.blockType().getSimpleName(),
                        previous.getClass().getSimpleName(), chunker.getClass().getSimpleName()));
            }
        }
        this.registry = Map.copyOf(table);
    }

    /**
     * Splits a Block list into ordered chunks. Indexes increase monotonically from 0
     */
    public List<Chunk> dispatch(List<Block> blocks, ChunkBudget budget) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }

        HeadingHandler.Outline outline = HeadingHandler.Outline.EMPTY;
        List<ChunkDraft> drafts = new ArrayList<>();
        for (Block block : blocks) {
            if (block instanceof HeadingBlock heading) {
                outline = headingHandler.update(outline, heading);
            }
            drafts.addAll(chunkOne(block, ChunkContext.of(outline.path(), budget)));
        }

        return ChunkAssembler.assembleAll(chunkPacker.pack(drafts, budget));
    }

    @SuppressWarnings("unchecked")
    private List<ChunkDraft> chunkOne(Block block, ChunkContext ctx) {
        BlockChunker<Block> chunker = (BlockChunker<Block>) registry.get(block.getClass());
        if (chunker == null) {
            throw new ServiceException("No chunker claims block type: " + block.getClass().getName());
        }
        return chunker.chunk(block, ctx);
    }
}
