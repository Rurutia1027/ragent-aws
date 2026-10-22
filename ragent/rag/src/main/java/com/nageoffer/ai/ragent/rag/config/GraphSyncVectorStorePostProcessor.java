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

import com.nageoffer.ai.ragent.rag.core.graph.LightRagClient;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreService;
import com.nageoffer.ai.ragent.rag.core.vector.decorator.GraphSyncingVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Weaver that syncs vector writes into the graph
 * <p>
 * Same shape as {@link KeywordSyncVectorStorePostProcessor}: when a {@link LightRagClient} is in the container
 * (rag.graph.type=lightrag), wrap the real {@link VectorStoreService} bean in
 * {@link GraphSyncingVectorStoreService} so every vector write also syncs the graph
 * <p>
 * Resolve lazily with {@link ObjectProvider} rather than @ConditionalOnBean: a BeanPostProcessor is created too early,
 * and conditional-registration order is unreliable. With lazy lookup, type != lightrag passes the original bean through at zero cost
 * <p>
 * Stacked with the keyword weaver, the decorator chain is GraphSyncing(KeywordSyncing(real implementation)): independent switches, no interaction, order does not matter
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphSyncVectorStorePostProcessor implements BeanPostProcessor {

    private final ObjectProvider<LightRagClient> lightRagClientProvider;

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
        if (bean instanceof VectorStoreService vectorStore
                && !(bean instanceof GraphSyncingVectorStoreService)) {
            LightRagClient lightRagClient = lightRagClientProvider.getIfAvailable();
            if (lightRagClient != null) {
                log.info("检测到图谱后端实现，向量写入将同步维护知识图谱, vectorStore={}", beanName);
                return new GraphSyncingVectorStoreService(vectorStore, lightRagClient);
            }
        }
        return bean;
    }
}
