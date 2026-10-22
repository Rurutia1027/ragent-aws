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

package com.nageoffer.ai.ragent.knowledge.sink;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.core.ingest.DocumentRef;
import com.nageoffer.ai.ragent.core.ingest.VectorTarget;
import com.nageoffer.ai.ragent.core.ingest.sink.ChunkSink;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.infra.token.TokenCounterService;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeChunkDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Relational target: writes {@code t_knowledge_chunk}, storing display text and embedding text together
 * <p>
 * {@code embedding_text} is stored not for display. It lets a model change re-embed without parsing again (skipping
 * the repeated cost of layout parsing and the vision model), and it lets a manually edited chunk recompute its embedding text correctly
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RelationalChunkSink implements ChunkSink {

    private final KnowledgeChunkMapper chunkMapper;
    private final TokenCounterService tokenCounterService;

    @Override
    public void replaceDocument(VectorTarget target, DocumentRef doc, List<EmbeddedChunk> chunks) {
        deleteDocument(target, doc);
        if (chunks.isEmpty()) {
            return;
        }
        String username = UserContext.getUsername();
        List<KnowledgeChunkDO> rows = new ArrayList<>(chunks.size());
        for (EmbeddedChunk chunk : chunks) {
            String content = chunk.content();
            rows.add(KnowledgeChunkDO.builder()
                    .id(chunk.chunkId())
                    .kbId(doc.kbId())
                    .docId(doc.docId())
                    .chunkIndex(chunk.index())
                    .content(content)
                    .contentHash(SecureUtil.sha256(content))
                    .charCount(content.length())
                    .tokenCount(StringUtils.hasText(content) ? tokenCounterService.countTokens(content) : 0)
                    .embeddingText(chunk.embeddingText())
                    .enabled(1)
                    .createdBy(username)
                    .updatedBy(username)
                    .build());
        }
        chunkMapper.insert(rows);
        log.debug("关系库块写入完成 docId={} 块数={}", doc.docId(), rows.size());
    }

    @Override
    public void deleteDocument(VectorTarget target, DocumentRef doc) {
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkDO>()
                .eq(KnowledgeChunkDO::getDocId, doc.docId()));
    }
}
