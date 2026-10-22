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

package com.nageoffer.ai.ragent.rag.config;

import com.nageoffer.ai.ragent.rag.core.keyword.KeywordIndexService;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreService;
import com.nageoffer.ai.ragent.rag.core.vector.decorator.KeywordSyncingVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Weaver for the vector-write decorator
 * <p>
 * When a {@link KeywordIndexService} is present in the container (rag.keyword.type != none),
 * wrap the real {@link VectorStoreService} bean in {@link KeywordSyncingVectorStoreService}
 * so every vector write also syncs the keyword index
 * <p>
 * Resolve lazily with {@link ObjectProvider} rather than @ConditionalOnBean: a BeanPostProcessor is created too early,
 * and conditional-registration order is unreliable. With lazy lookup, type=none passes the original bean through at zero cost
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordSyncVectorStorePostProcessor implements BeanPostProcessor {

    private final ObjectProvider<KeywordIndexService> keywordIndexServiceProvider;

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
        if (bean instanceof VectorStoreService vectorStore
                && !(bean instanceof KeywordSyncingVectorStoreService)) {
            KeywordIndexService keywordIndexService = keywordIndexServiceProvider.getIfAvailable();
            if (keywordIndexService != null) {
                log.info("检测到关键词索引实现，向量写入将同步维护关键词索引, vectorStore={}", beanName);
                return new KeywordSyncingVectorStoreService(vectorStore, keywordIndexService);
            }
        }
        return bean;
    }
}
