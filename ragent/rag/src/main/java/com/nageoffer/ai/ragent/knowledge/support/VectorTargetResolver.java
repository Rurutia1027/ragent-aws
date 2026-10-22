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

package com.nageoffer.ai.ragent.knowledge.support;

import com.nageoffer.ai.ragent.core.ingest.VectorTarget;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.rag.config.RAGDefaultProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Derives the vector target: knowledge-base config (L2) + deployment config (L1) -> {@link VectorTarget}
 * <p>
 * A separate component so "how the target identity is computed" has one place it comes from. Each vector-write
 * call site used to read the model from the knowledge base and decide for itself whether to fall back to the
 * system default, so the upload path used the knowledge-base model and the pipeline path used the system default,
 * and one partition mixed vectors from two semantic spaces
 */
@Component
@RequiredArgsConstructor
public class VectorTargetResolver {

    private final RAGDefaultProperties ragDefaultProperties;

    /**
     * Derives the target. Missing config fails immediately instead of falling back to a default
     */
    public VectorTarget resolve(KnowledgeBaseDO kbDO) {
        if (kbDO == null) {
            throw new ClientException("Knowledge base does not exist");
        }
        if (!StringUtils.hasText(kbDO.getEmbeddingModel())) {
            throw new ClientException("Knowledge base has no embedding model configured: kbId=" + kbDO.getId());
        }
        Integer dimension = ragDefaultProperties.getDimension();
        if (dimension == null || dimension <= 0) {
            throw new ClientException("Deployment has no vector dimension configured: rag.default.dimension");
        }
        return new VectorTarget(kbDO.getCollectionName(), kbDO.getEmbeddingModel(), dimension);
    }
}
