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

package com.nageoffer.ai.ragent.knowledge.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.framework.mq.KafkaMessages;
import com.nageoffer.ai.ragent.framework.mq.MessageWrapper;
import com.nageoffer.ai.ragent.knowledge.mq.event.KnowledgeBaseCleanupEvent;
import com.nageoffer.ai.ragent.rag.core.graph.LightRagClient;
import com.nageoffer.ai.ragent.rag.core.keyword.KeywordIndexService;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreAdmin;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Knowledge-base delete cleanup consumer.
 * Asynchronously reclaims physical resources owned by the knowledge base: vectors, the bucket, the ES keyword index, and knowledge-graph data.
 * <p>
 * Cleanup items do not affect each other. Any failure throws, the offset is not committed, and Kafka retries. Every operation is idempotent, so retry is safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseCleanupConsumer {

    private final VectorStoreAdmin vectorStoreAdmin;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    /**
     * The keyword index is resolved lazily. When rag.keyword.type=none there is no bean, getIfAvailable() returns null, and ES cleanup is skipped
     */
    private final ObjectProvider<KeywordIndexService> keywordIndexServiceProvider;
    /**
     * The graph client is resolved lazily. When rag.graph.type=none there is no bean, getIfAvailable() returns null, and graph cleanup is skipped
     */
    private final ObjectProvider<LightRagClient> lightRagClientProvider;

    @KafkaListener(
            topics = "knowledge-base-cleanup_topic${unique-name:}",
            groupId = "knowledge-base-cleanup_cg${unique-name:}"
    )
    public void onMessage(String payload, Acknowledgment acknowledgment) throws Exception {
        MessageWrapper<KnowledgeBaseCleanupEvent> message =
                KafkaMessages.read(objectMapper, payload, KnowledgeBaseCleanupEvent.class);
        KnowledgeBaseCleanupEvent event = message.getBody();
        String collectionName = event.getCollectionName();

        log.info("[消费者] 开始清理知识库物理资源，kbId={}, collectionName={}", event.getKbId(), collectionName);

        boolean allSucceeded = true;

        try {
            vectorStoreAdmin.dropVectorSpace(collectionName);
        } catch (Exception e) {
            allSucceeded = false;
            log.error("清理向量空间失败，collectionName={}", collectionName, e);
        }

        try {
            fileStorageService.deleteKnowledgeSpace(collectionName);
        } catch (Exception e) {
            allSucceeded = false;
            log.error("删除知识库存储目录失败，namespace={}", collectionName, e);
        }

        KeywordIndexService keywordIndexService = keywordIndexServiceProvider.getIfAvailable();
        if (keywordIndexService != null) {
            try {
                keywordIndexService.deleteByCollection(collectionName);
            } catch (Exception e) {
                allSucceeded = false;
                log.error("删除 ES 关键词索引失败，collectionName={}", collectionName, e);
            }
        }

        LightRagClient lightRagClient = lightRagClientProvider.getIfAvailable();
        if (lightRagClient != null) {
            try {
                lightRagClient.deleteByCollection(collectionName);
            } catch (Exception e) {
                allSucceeded = false;
                log.error("删除 LightRAG 图谱数据失败，collectionName={}", collectionName, e);
            }
        }

        if (!allSucceeded) {
            throw new ServiceException("Knowledge base physical cleanup had failures and will be retried");
        }
        acknowledgment.acknowledge();
    }
}
