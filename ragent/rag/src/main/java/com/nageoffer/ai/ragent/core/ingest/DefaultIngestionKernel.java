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

package com.nageoffer.ai.ragent.core.ingest;

import com.nageoffer.ai.ragent.core.chunk.ChunkingService;
import com.nageoffer.ai.ragent.core.chunk.model.Chunk;
import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.core.ingest.embed.ChunkEmbeddingService;
import com.nageoffer.ai.ragent.core.ingest.sink.ChunkIndexWriter;
import com.nageoffer.ai.ragent.core.parser.DocumentParser;
import com.nageoffer.ai.ragent.core.parser.mime.MimeTypeDetector;
import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.registry.ParserRegistry;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default ingestion kernel: a fixed five-step skeleton, the only ingestion sequence in the system
 * <p>
 * The entry takes neither a MIME type nor an embedding model, and it does not touch task status or ingestion logs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultIngestionKernel implements IngestionKernel {

    /**
     * Parser options key: original file name, written into chunk provenance
     */
    private static final String OPT_SOURCE_FILE = "sourceFile";

    /**
     * Parser options key: document id, which decides the image-asset directory {@code assets/{docId}/...}
     */
    private static final String OPT_DOCUMENT_ID = "documentId";

    private final ParserRegistry parserRegistry;
    private final ChunkingService chunkingService;
    private final ChunkEmbeddingService chunkEmbeddingService;
    private final ChunkIndexWriter chunkIndexWriter;

    @Override
    public IngestionOutcome run(DocumentRef doc,
                                byte[] bytes,
                                IngestionSpec spec,
                                VectorTarget target) {
        if (bytes == null || bytes.length == 0) {
            throw new ClientException("File content is empty: docId=" + doc.docId());
        }
        IngestionSpec effectiveSpec = spec == null ? IngestionSpec.defaults() : spec;

        // (1) identity: the only type detection on the whole path
        String mimeType = MimeTypeDetector.detect(bytes, doc.filename());
        if (!StringUtils.hasText(mimeType)) {
            throw new ClientException("Could not detect the file type: docId=" + doc.docId() + ", filename=" + doc.filename());
        }

        // (2) parse: (MIME x profile) -> parser
        long parseStart = System.currentTimeMillis();
        DocumentParser parser = parserRegistry.require(mimeType, effectiveSpec.parseProfile());
        ParsedDocument parsed = parser.parseStructured(bytes, mimeType, parserOptions(doc));
        List<Block> blocks = parsed.blocks() == null ? List.of() : parsed.blocks();
        long parseMillis = System.currentTimeMillis() - parseStart;
        log.info("摄取-解析完成 docId={} mime={} 档位={} 解析器={} blocks={}",
                doc.docId(), mimeType, effectiveSpec.parseProfile().getCode(), parser.getParserType(), blocks.size());

        // (3) chunk: Block type -> chunker + budget
        long chunkStart = System.currentTimeMillis();
        List<Chunk> chunks = chunkingService.chunk(blocks, effectiveSpec.budget());
        long chunkMillis = System.currentTimeMillis() - chunkStart;

        if (chunks.isEmpty()) {
            throw new ClientException("Chunk result is empty: docId=" + doc.docId() + ", mime=" + mimeType);
        }

        // (4) embed: model and dimension both come from the target; dimension is checked here
        long embedStart = System.currentTimeMillis();
        List<EmbeddedChunk> embedded = chunkEmbeddingService.embed(chunks, target);
        long embedMillis = System.currentTimeMillis() - embedStart;

        // (5) index: fan out to every target; the transaction boundary is inside the writer
        long indexStart = System.currentTimeMillis();
        chunkIndexWriter.replaceDocument(target, doc, embedded);
        long indexMillis = System.currentTimeMillis() - indexStart;

        return new IngestionOutcome(mimeType, parser.getParserType(), blocks.size(), chunks,
                new IngestionOutcome.IngestionTimings(parseMillis, chunkMillis, embedMillis, indexMillis));
    }

    /**
     * Builds parser input. docId is required; the parser uses it to name image assets. Omit it and the assets land in a random directory, detached from the document
     */
    private Map<String, Object> parserOptions(DocumentRef doc) {
        Map<String, Object> options = new HashMap<>();
        if (StringUtils.hasText(doc.filename())) {
            options.put(OPT_SOURCE_FILE, doc.filename());
        }
        options.put(OPT_DOCUMENT_ID, doc.docId());
        return options;
    }
}
