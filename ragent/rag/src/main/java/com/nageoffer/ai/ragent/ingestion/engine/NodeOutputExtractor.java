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

package com.nageoffer.ai.ragent.ingestion.engine;

import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.ingestion.domain.context.DocumentSource;
import com.nageoffer.ai.ragent.ingestion.domain.context.IngestionContext;
import com.nageoffer.ai.ragent.ingestion.domain.enums.IngestionNodeType;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.NodeConfig;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Node output extractor: takes a summary of each node's output from {@code IngestionContext}
 * <p>
 * Summaries only, never entities. Output lands in {@code t_ingestion_task_node.output_json}, a TEXT column
 * truncated at 1MB and used only for diagnosis. Stuffing an entity in (source bytes / every Block / chunks with
 * vectors) always blows that cap, and what remains after truncation is JSON that cannot be parsed. Each entity
 * already has a source of truth: the source file in object storage, the chunks in {@code t_knowledge_chunk} and the vector store
 */
@Component
public class NodeOutputExtractor {

    public Map<String, Object> extract(IngestionContext context, NodeConfig config) {
        if (context == null || config == null) {
            return Map.of();
        }
        IngestionNodeType nodeType = resolveNodeType(config.getNodeType());
        if (nodeType == null) {
            return genericOutput(context);
        }
        return switch (nodeType) {
            case FETCHER -> fetcherOutput(context);
            case PARSER -> parserOutput(context);
            case ENHANCER -> enhancerOutput(context);
            case CHUNKER -> chunkerOutput(context);
            case ENRICHER -> enricherOutput(context);
            case INDEXER -> indexerOutput(context, config);
        };
    }

    private Map<String, Object> fetcherOutput(IngestionContext context) {
        Map<String, Object> output = new LinkedHashMap<>();
        DocumentSource source = context.getSource();
        if (source != null) {
            Map<String, Object> sourceView = new LinkedHashMap<>();
            sourceView.put("type", source.getType() == null ? null : source.getType().getValue());
            sourceView.put("location", source.getLocation());
            sourceView.put("fileName", source.getFileName());
            output.put("source", sourceView);
        }
        output.put("mimeType", context.getMimeType());
        byte[] raw = context.getRawBytes();
        if (raw != null) {
            // Record the length, not the content. Base64 of the source file is about 1.33x, and past ~750KB this record hits the truncation cap
            output.put("rawBytesLength", raw.length);
        }
        return output;
    }

    /**
     * Parser node output: a summary of block structure only, not every Block
     * <p>
     * {@code rawText} is enough for the body. This only answers "what was cut out". Storing the Blocks too would
     * store the body twice in one record, and a slightly large document hits the 1MB output_json cap
     */
    private Map<String, Object> parserOutput(IngestionContext context) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("mimeType", context.getMimeType());
        output.put("rawText", context.getRawText());
        List<Block> blocks = context.getDocument() == null || context.getDocument().getBlocks() == null
                ? List.of()
                : context.getDocument().getBlocks();
        output.put("blockCount", blocks.size());
        output.put("blockTypes", countByType(blocks));
        return output;
    }

    /**
     * Counts by concrete Block type, for example {@code {ParagraphBlock=12, TableBlock=1}}
     */
    private static Map<String, Long> countByType(List<Block> blocks) {
        return blocks.stream().collect(Collectors.groupingBy(
                block -> block.getClass().getSimpleName(),
                LinkedHashMap::new,
                Collectors.counting()));
    }

    private Map<String, Object> enhancerOutput(IngestionContext context) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("enhancedText", context.getEnhancedText());
        output.put("keywords", context.getKeywords());
        output.put("questions", context.getQuestions());
        output.put("metadata", context.getMetadata());
        return output;
    }

    private Map<String, Object> chunkerOutput(IngestionContext context) {
        return chunkSummary(safeChunks(context));
    }

    /**
     * An enrichment node also reports the extension-slot key names. Enrichment's only change is extras.
     * Without that list this output is byte-for-byte the chunk node's, and you cannot tell whether enrichment ran
     */
    private Map<String, Object> enricherOutput(IngestionContext context) {
        List<EmbeddedChunk> chunks = safeChunks(context);
        Map<String, Object> output = chunkSummary(chunks);
        output.put("extraKeys", collectExtraKeys(chunks));
        return output;
    }

    private Map<String, Object> indexerOutput(IngestionContext context, NodeConfig config) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("settings", config.getSettings());
        output.putAll(chunkSummary(safeChunks(context)));
        return output;
    }

    /**
     * Chunk summary shared by the chunk, enrichment, and index nodes
     * <p>
     * Do not store the whole chunk. {@link EmbeddedChunk} carries the full embedding array; one 1536-d vector
     * serializes to about 15KB, and a hundred chunks blow the 1MB cap, especially since the same chunks are stored
     * once per node. {@code embeddingDim} is worth keeping: it is the evidence when one partition is mixed with vectors from two semantic spaces
     */
    private static Map<String, Object> chunkSummary(List<EmbeddedChunk> chunks) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("chunkCount", chunks.size());
        summary.put("totalChars", chunks.stream()
                .mapToInt(chunk -> chunk.content() == null ? 0 : chunk.content().length())
                .sum());
        summary.put("embeddingDim", chunks.isEmpty() ? 0 : chunks.get(0).dimension());
        return summary;
    }

    private static Set<String> collectExtraKeys(List<EmbeddedChunk> chunks) {
        return chunks.stream()
                .flatMap(chunk -> chunk.metadata().extras().keySet().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The extractor is also called on the node-failure branch, and that call is not inside any try. Prefer an extra null check here
     */
    private static List<EmbeddedChunk> safeChunks(IngestionContext context) {
        if (context.getChunks() == null) {
            return List.of();
        }
        return context.getChunks().stream().filter(Objects::nonNull).toList();
    }

    private Map<String, Object> genericOutput(IngestionContext context) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("mimeType", context.getMimeType());
        output.put("rawText", context.getRawText());
        output.put("enhancedText", context.getEnhancedText());
        output.put("keywords", context.getKeywords());
        output.put("questions", context.getQuestions());
        output.put("metadata", context.getMetadata());
        output.putAll(chunkSummary(safeChunks(context)));
        return output;
    }

    private IngestionNodeType resolveNodeType(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) {
            return null;
        }
        try {
            return IngestionNodeType.fromValue(nodeType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
