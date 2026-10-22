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

package com.nageoffer.ai.ragent.core.ingest.embed;

import com.nageoffer.ai.ragent.core.chunk.model.Chunk;
import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.core.ingest.VectorTarget;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding: model and dimension both come from {@link VectorTarget}. There is no nullable argument that can fall back to the system default model
 * <p>
 * Lives in the orchestration layer, not the chunk layer. A target is an orchestration concept; putting it in the
 * chunk layer would make the modality layer depend back on orchestration
 */
@Service
@RequiredArgsConstructor
public class ChunkEmbeddingService {

    private final EmbeddingService embeddingService;

    /**
     * Computes a vector for each chunk and checks the dimension of every one. The physical column width is fixed in
     * the DDL; without this check the error surfaces only when the vector store casts the type
     *
     * @param chunks chunks to embed; assembly has already guaranteed the embedding text is non-empty
     * @param target vector target: supplies the model and the dimension that must match
     * @return embedded chunks, one per input, in the same order
     */
    public List<EmbeddedChunk> embed(List<Chunk> chunks, VectorTarget target) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<String> texts = chunks.stream().map(Chunk::embeddingText).toList();
        List<List<Float>> vectors = embeddingService.embedBatch(texts, target.embeddingModel());
        if (vectors == null || vectors.size() != chunks.size()) {
            throw new ServiceException(String.format("Vector result count does not match chunks: expected %d, actual %s",
                    chunks.size(), vectors == null ? "null" : String.valueOf(vectors.size())));
        }

        List<EmbeddedChunk> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            List<Float> row = vectors.get(i);
            if (row == null || row.isEmpty()) {
                throw new ServiceException("Vector result is missing, index: " + i);
            }
            if (row.size() != target.dimension()) {
                throw new ServiceException(String.format(
                        "Embedding dimension does not match the deployment vector space: model %s returned %d dimensions, the physical space requires %d (partition %s)"
                                + "——请改用同维度的嵌入模型，或调整部署级维度并重建向量空间",
                        target.embeddingModel(), row.size(), target.dimension(), target.partition()));
            }
            float[] vector = new float[row.size()];
            for (int j = 0; j < row.size(); j++) {
                vector[j] = row.get(j);
            }
            result.add(new EmbeddedChunk(chunks.get(i), vector));
        }
        return result;
    }
}
