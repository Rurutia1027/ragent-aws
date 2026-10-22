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

package com.nageoffer.ai.ragent.ingestion.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.core.chunk.ChunkingService;
import com.nageoffer.ai.ragent.core.chunk.model.Chunk;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkBudget;
import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.core.ingest.VectorTarget;
import com.nageoffer.ai.ragent.core.ingest.embed.ChunkEmbeddingService;
import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.ingestion.domain.context.IngestionContext;
import com.nageoffer.ai.ragent.ingestion.domain.enums.IngestionNodeType;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.NodeConfig;
import com.nageoffer.ai.ragent.ingestion.domain.result.NodeResult;
import com.nageoffer.ai.ragent.ingestion.domain.settings.ChunkerSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Chunk node: split a Block list by budget and embed the chunks
 * <p>
 * Two adaptations narrowed when this moved into the kernel: chunk parameters collapsed from "a strategy enum
 * plus a bag of free keys" into {@link ChunkBudget}, and embedding changed from {@code embed(chunks, null)} to
 * the vector target in context, so this path no longer uses a different model from the upload path
 */
@Component
@RequiredArgsConstructor
public class ChunkerNode implements IngestionNode {

    /**
     * No-split sentinel. Keeps the frontend's existing {@code -1} and translates it here into a whole-document budget
     */
    private static final int WHOLE_DOCUMENT_SENTINEL = -1;

    private final ObjectMapper objectMapper;
    private final ChunkEmbeddingService chunkEmbeddingService;
    private final ChunkingService chunkingService;

    @Override
    public String getNodeType() {
        return IngestionNodeType.CHUNKER.getValue();
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        VectorTarget target = context.getVectorTarget();
        if (target == null) {
            return NodeResult.fail(new ClientException("Chunk node is missing a vector target (partition / embedding model / dimension)"));
        }

        List<Block> blocks = context.getDocument() == null ? null : context.getDocument().getBlocks();
        List<Chunk> chunks = chunkingService.chunk(blocks, toBudget(parseSettings(config.getSettings())));
        if (chunks.isEmpty()) {
            return NodeResult.fail(new ClientException("Chunk result is empty"));
        }

        List<EmbeddedChunk> embedded = chunkEmbeddingService.embed(chunks, target);
        context.setChunks(embedded);
        return NodeResult.ok("已分块 " + embedded.size() + " 段");
    }

    private ChunkerSettings parseSettings(JsonNode node) {
        ChunkerSettings settings = objectMapper.convertValue(node, ChunkerSettings.class);
        return settings == null ? ChunkerSettings.builder().build() : settings;
    }

    /**
     * Translates the three integers in the pipeline settings into a budget. Missing or illegal values take the system default, and there is only one default
     */
    private ChunkBudget toBudget(ChunkerSettings settings) {
        Integer chunkSize = settings.getChunkSize();
        if (chunkSize != null && chunkSize == WHOLE_DOCUMENT_SENTINEL) {
            return ChunkBudget.wholeDocument();
        }
        ChunkBudget defaults = ChunkBudget.defaults();
        int maxChars = chunkSize != null && chunkSize > 0 ? chunkSize : defaults.maxChars();
        // When overlap is omitted, scale it to the chunk size instead of copying the number in the default budget, which is paired with 1024
        int overlap = settings.getOverlapSize() != null && settings.getOverlapSize() >= 0
                ? settings.getOverlapSize()
                : ChunkBudget.defaultOverlapFor(maxChars);
        // Overlap must be less than the chunk size, or splitting cannot make progress
        if (overlap >= maxChars) {
            overlap = Math.max(0, maxChars - 1);
        }
        int rowsPerChunk = settings.getRowsPerChunk() != null && settings.getRowsPerChunk() > 0
                ? settings.getRowsPerChunk()
                : defaults.rowsPerChunk();
        return new ChunkBudget(maxChars, overlap, rowsPerChunk);
    }
}
