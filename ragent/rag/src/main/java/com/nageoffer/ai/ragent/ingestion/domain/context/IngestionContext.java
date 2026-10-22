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

package com.nageoffer.ai.ragent.ingestion.domain.context;

import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.core.ingest.VectorTarget;
import com.nageoffer.ai.ragent.core.parser.model.AssetRef;
import com.nageoffer.ai.ragent.ingestion.domain.enums.IngestionStatus;
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Document ingestion context.
 * Carries every intermediate value and status while an ingestion pipeline runs
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionContext {

    /**
     * Unique id of the ingestion task
     */
    private String taskId;

    /**
     * Pipeline id that runs this ingestion
     */
    private String pipelineId;

    /**
     * Document source
     */
    private DocumentSource source;

    /**
     * Raw bytes of the document
     */
    private byte[] rawBytes;

    /**
     * MIME type of the document
     */
    private String mimeType;

    /**
     * Raw text after parsing
     */
    private String rawText;

    /**
     * Document object after structured parsing
     */
    private StructuredDocument document;

    /**
     * Text chunks after the document is split
     */
    private List<EmbeddedChunk> chunks;

    /**
     * Vector target: logical partition + embedding model + dimension, derived by the caller from knowledge-base config
     * <p>
     * The chunk node used to call {@code embed(chunks, null)} and fall back to the system default model, which did
     * not match the knowledge-base model used on the upload path, so one partition could mix vectors from two
     * semantic spaces. Once the target is passed explicitly, that failure mode cannot be expressed
     */
    private VectorTarget vectorTarget;

    /**
     * Text after enhancement
     */
    private String enhancedText;

    /**
     * Keywords extracted from the document
     */
    private List<String> keywords;

    /**
     * Questions generated from the document
     */
    private List<String> questions;

    /**
     * Metadata collected during ingestion
     */
    private Map<String, Object> metadata;

    /**
     * Vector space id: the target collection the vectors are written to.
     * When omitted, the default vector space is used
     */
    private VectorSpaceId vectorSpaceId;

    /**
     * Status of the current ingestion task
     */
    private IngestionStatus status;

    /**
     * Node logs collected while the pipeline runs
     */
    private List<NodeLog> logs;

    /**
     * Exception raised during ingestion
     */
    private Throwable error;

    /**
     * Whether to skip IndexerNode's vector write.
     * When true, IndexerNode only validates and does not write; the caller persists vectors in one transaction
     */
    @Builder.Default
    private boolean skipIndexerWrite = false;

    /**
     * Document-level asset references (introduced by the v1.1 multimodal parse change)
     * <p>
     * Produced by DocumentParser during parsing (for example after MinerU unpacks images and uploads them to RustFS).
     * ChunkerNode writes the matching AssetRef back into the matching chunk's metadata
     */
    @Builder.Default
    private List<AssetRef> assets = new ArrayList<>();
}
