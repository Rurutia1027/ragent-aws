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

package com.nageoffer.ai.ragent.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mzt.logapi.starter.annotation.LogRecord;
import com.nageoffer.ai.ragent.audit.constant.BizChangeBizType;
import com.nageoffer.ai.ragent.audit.constant.BizChangeOperationType;
import com.nageoffer.ai.ragent.audit.support.BizChangeLogContext;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeChunkBatchRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeChunkCreateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeChunkPageRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeChunkUpdateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.vo.KnowledgeChunkVO;
import com.nageoffer.ai.ragent.core.chunk.model.Chunk;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkAssembler;
import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.core.ingest.VectorTarget;
import com.nageoffer.ai.ragent.core.ingest.embed.ChunkEmbeddingService;
import com.nageoffer.ai.ragent.knowledge.support.VectorTargetResolver;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeChunkDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.infra.token.TokenCounterService;
import com.nageoffer.ai.ragent.knowledge.enums.DocumentStatus;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreService;
import com.nageoffer.ai.ragent.knowledge.service.KnowledgeChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import cn.hutool.crypto.SecureUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Knowledge-base chunk service implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ChunkEmbeddingService chunkEmbeddingService;
    private final VectorTargetResolver vectorTargetResolver;
    private final TokenCounterService tokenCounterService;
    private final VectorStoreService vectorStoreService;
    private final TransactionOperations transactionOperations;
    private final BizChangeLogContext bizChangeLogContext;

    @Override
    public IPage<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest requestParam) {
        KnowledgeDocumentDO documentDO = documentMapper.selectById(docId);
        Assert.notNull(documentDO, () -> new ClientException("Document does not exist"));

        LambdaQueryWrapper<KnowledgeChunkDO> queryWrapper = new LambdaQueryWrapper<KnowledgeChunkDO>()
                .eq(KnowledgeChunkDO::getDocId, docId)
                .eq(requestParam.getEnabled() != null, KnowledgeChunkDO::getEnabled, requestParam.getEnabled())
                .orderByAsc(KnowledgeChunkDO::getChunkIndex);

        Page<KnowledgeChunkDO> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<KnowledgeChunkDO> result = chunkMapper.selectPage(page, queryWrapper);
        return result.convert(each -> BeanUtil.toBean(each, KnowledgeChunkVO.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(
            success = "新增 Chunk：{{#_ret.id}}",
            fail = "新增 Chunk 失败：{{#_errorMsg}}",
            type = BizChangeBizType.KNOWLEDGE_CHUNK,
            subType = BizChangeOperationType.CREATE,
            bizNo = "{{#bizChangeBizId != null ? #bizChangeBizId : #docId}}",
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    public KnowledgeChunkVO create(String docId, KnowledgeChunkCreateRequest requestParam) {
        KnowledgeDocumentDO documentDO = documentMapper.selectById(docId);
        Assert.notNull(documentDO, () -> new ClientException("Document does not exist"));
        if (DocumentStatus.RUNNING.getCode().equals(documentDO.getStatus())) {
            throw new ClientException("Document is being chunked, so a new chunk cannot be added");
        }
        if (!Integer.valueOf(1).equals(documentDO.getEnabled())) {
            throw new ClientException("Document is disabled, so a new chunk cannot be added");
        }

        String content = requestParam.getContent();
        Assert.notBlank(content, () -> new ClientException("Chunk content cannot be empty"));

        KnowledgeChunkDO latest = chunkMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                        .eq(KnowledgeChunkDO::getDocId, docId)
                        .orderByDesc(KnowledgeChunkDO::getChunkIndex)
                        .last("LIMIT 1")
        );
        int chunkIndex = requestParam.getIndex() != null
                ? requestParam.getIndex()
                : (latest != null ? latest.getChunkIndex() + 1 : 0);

        String contentHash = SecureUtil.sha256(content);
        int charCount = content.length();
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(documentDO.getKbId());
        String embeddingModel = kbDO.getEmbeddingModel();
        String collectionName = kbDO.getCollectionName();
        Integer tokenCount = resolveTokenCount(content);

        KnowledgeChunkDO chunkDO = KnowledgeChunkDO.builder()
                .id(requestParam.getChunkId())
                .kbId(documentDO.getKbId())
                .docId(docId)
                .chunkIndex(chunkIndex)
                .content(content)
                .contentHash(contentHash)
                .charCount(charCount)
                .tokenCount(tokenCount)
                // A manual chunk has no structure, so embedding text equals the body. Write it explicitly instead of leaving it empty, so a rebuild does not have to guess
                .embeddingText(content)
                .enabled(1)
                .createdBy(UserContext.getUsername())
                .updatedBy(UserContext.getUsername())
                .build();

        chunkMapper.insert(chunkDO);
        log.info("新增 Chunk 成功, kbId={}, docId={}, chunkId={}, chunkIndex={}", documentDO.getKbId(), docId, chunkDO.getId(), chunkIndex);

        documentMapper.update(Wrappers.lambdaUpdate(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getId, docId)
                .setSql("chunk_count = chunk_count + 1"));

        // Write through to the vector store
        syncChunkToVector(collectionName, docId, chunkDO, vectorTargetResolver.resolve(kbDO));

        bizChangeLogContext.put(String.valueOf(chunkDO.getId()), null, chunkDO);
        return BeanUtil.toBean(chunkDO, KnowledgeChunkVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(
            success = "更新 Chunk：{{#chunkId}}",
            fail = "更新 Chunk 失败：{{#_errorMsg}}",
            type = BizChangeBizType.KNOWLEDGE_CHUNK,
            subType = BizChangeOperationType.UPDATE,
            bizNo = "{{#chunkId}}",
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    public void update(String docId, String chunkId, KnowledgeChunkUpdateRequest requestParam) {
        KnowledgeDocumentDO documentDO = documentMapper.selectById(docId);
        Assert.notNull(documentDO, () -> new ClientException("Document does not exist"));
        if (DocumentStatus.RUNNING.getCode().equals(documentDO.getStatus())) {
            throw new ClientException("Document is being chunked, so the chunk cannot be modified");
        }

        KnowledgeChunkDO chunkDO = chunkMapper.selectById(chunkId);
        Assert.notNull(chunkDO, () -> new ClientException("Chunk does not exist"));
        Assert.isTrue(chunkDO.getDocId().equals(docId), () -> new ClientException("Chunk does not belong to this document"));
        KnowledgeChunkDO before = BeanUtil.copyProperties(chunkDO, KnowledgeChunkDO.class);

        String newContent = requestParam.getContent();
        Assert.notBlank(newContent, () -> new ClientException("Chunk content cannot be empty"));

        if (newContent.equals(chunkDO.getContent())) {
            bizChangeLogContext.skip();
            return;
        }

        chunkDO.setContent(newContent);
        chunkDO.setContentHash(SecureUtil.sha256(newContent));
        chunkDO.setCharCount(newContent.length());
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(documentDO.getKbId());
        String embeddingModel = kbDO.getEmbeddingModel();
        String collectionName = kbDO.getCollectionName();
        chunkDO.setTokenCount(resolveTokenCount(newContent));
        // Embedding text must change with the body. Otherwise the vector is updated from the new body while the stored text is still the old one, and the next rebuild uses the wrong text
        chunkDO.setEmbeddingText(newContent);
        chunkDO.setUpdatedBy(UserContext.getUsername());

        chunkMapper.updateById(chunkDO);

        log.info("更新 Chunk 成功, kbId={}, docId={}, chunkId={}", documentDO.getKbId(), docId, chunkId);

        // Once a chunk or document is disabled, embedding would let retrieval hit vectors that are not allowed
        if(Integer.valueOf(0).equals(chunkDO.getEnabled()) || Integer.valueOf(0).equals(documentDO.getEnabled())){
            log.warn("分块或文档未启用，暂不执行向量化操作");
        }else{
            vectorStoreService.updateChunk(collectionName, docId,
                    embedPersisted(List.of(chunkDO), vectorTargetResolver.resolve(kbDO)).get(0));
        }
        bizChangeLogContext.put(chunkId, before, chunkMapper.selectById(chunkId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(
            success = "删除 Chunk：{{#chunkId}}",
            fail = "删除 Chunk 失败：{{#_errorMsg}}",
            type = BizChangeBizType.KNOWLEDGE_CHUNK,
            subType = BizChangeOperationType.DELETE,
            bizNo = "{{#chunkId}}",
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    public void delete(String docId, String chunkId) {
        KnowledgeDocumentDO documentDO = documentMapper.selectById(docId);
        Assert.notNull(documentDO, () -> new ClientException("Document does not exist"));
        if (DocumentStatus.RUNNING.getCode().equals(documentDO.getStatus())) {
            throw new ClientException("Document is being chunked, so the chunk cannot be deleted");
        }

        KnowledgeChunkDO chunkDO = chunkMapper.selectById(chunkId);
        Assert.notNull(chunkDO, () -> new ClientException("Chunk does not exist"));
        Assert.isTrue(chunkDO.getDocId().equals(docId), () -> new ClientException("Chunk does not belong to this document"));
        KnowledgeChunkDO before = BeanUtil.copyProperties(chunkDO, KnowledgeChunkDO.class);

        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(documentDO.getKbId());
        Assert.notNull(kbDO, () -> new ServiceException("Knowledge base does not exist"));
        String collectionName = kbDO.getCollectionName();

        chunkMapper.deleteById(chunkId);

        documentMapper.update(Wrappers.lambdaUpdate(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getId, docId)
                .setSql("chunk_count = CASE WHEN chunk_count > 0 THEN chunk_count - 1 ELSE 0 END"));

        log.info("删除 Chunk 成功, kbId={}, docId={}, chunkId={}", documentDO.getKbId(), docId, chunkId);

        deleteChunkFromVector(collectionName, chunkId);
        bizChangeLogContext.put(chunkId, before, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(
            success = "{{#enabled ? '启用' : '禁用'}} Chunk：{{#chunkId}}",
            fail = "修改 Chunk 启用状态失败：{{#_errorMsg}}",
            type = BizChangeBizType.KNOWLEDGE_CHUNK,
            subType = "{{#enabled ? 'ENABLE' : 'DISABLE'}}",
            bizNo = "{{#chunkId}}",
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    public void enableChunk(String docId, String chunkId, boolean enabled) {
        KnowledgeDocumentDO documentDO = documentMapper.selectById(docId);
        Assert.notNull(documentDO, () -> new ClientException("Document does not exist"));
        if (DocumentStatus.RUNNING.getCode().equals(documentDO.getStatus())) {
            throw new ClientException("Document is being chunked, so the chunk status cannot be modified");
        }
        validateDocumentEnabledForChunkEnable(documentDO, enabled);

        KnowledgeChunkDO chunkDO = chunkMapper.selectById(chunkId);
        Assert.notNull(chunkDO, () -> new ClientException("Chunk does not exist"));
        Assert.isTrue(chunkDO.getDocId().equals(docId), () -> new ClientException("Chunk does not belong to this document"));
        KnowledgeChunkDO before = BeanUtil.copyProperties(chunkDO, KnowledgeChunkDO.class);

        // If the status did not change, return
        int enabledValue = enabled ? 1 : 0;
        if (chunkDO.getEnabled().equals(enabledValue)) {
            bizChangeLogContext.skip();
            return;
        }

        chunkDO.setEnabled(enabledValue);
        chunkDO.setUpdatedBy(UserContext.getUsername());
        chunkMapper.updateById(chunkDO);

        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(documentDO.getKbId());
        String collectionName = kbDO.getCollectionName();
        log.info("{}Chunk 成功, kbId={}, docId={}, chunkId={}", enabled ? "启用" : "禁用", documentDO.getKbId(), docId, chunkId);

        if (enabled) {
            String embeddingModel = kbDO.getEmbeddingModel();
            syncChunkToVector(collectionName, docId, chunkDO, vectorTargetResolver.resolve(kbDO));
        } else {
            deleteChunkFromVector(collectionName, chunkId);
        }
        bizChangeLogContext.put(chunkId, before, chunkMapper.selectById(chunkId));
    }

    @Override
    @LogRecord(
            success = "批量{{#enabled ? '启用' : '禁用'}} Chunk：{{#docId}}",
            fail = "批量修改 Chunk 启用状态失败：{{#_errorMsg}}",
            type = BizChangeBizType.KNOWLEDGE_CHUNK,
            subType = "{{#enabled ? 'ENABLE' : 'DISABLE'}}",
            bizNo = "{{#docId}}",
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    public void batchToggleEnabled(String docId, KnowledgeChunkBatchRequest requestParam, boolean enabled) {
        if (requestParam == null || CollUtil.isEmpty(requestParam.getChunkIds())) {
            throw new ClientException("Specify the chunks to update. To enable or disable all chunks, use the document enable API");
        }
        List<String> requestedIds = requestParam.getChunkIds();
        if (requestedIds.size() > 500) {
            throw new ClientException("A single batch cannot update more than 500 chunks");
        }

        KnowledgeDocumentDO documentDO = documentMapper.selectById(docId);
        Assert.notNull(documentDO, () -> new ClientException("Document does not exist"));
        if (DocumentStatus.RUNNING.getCode().equals(documentDO.getStatus())) {
            throw new ClientException("Document is being chunked, so chunk status cannot be updated in batch");
        }
        validateDocumentEnabledForChunkEnable(documentDO, enabled);

        List<KnowledgeChunkDO> found = chunkMapper.selectByIds(requestedIds);
        if (found.size() != requestedIds.size()) {
            throw new ClientException("Invalid chunk IDs. Requested " + requestedIds.size() + ", found " + found.size());
        }
        found.forEach(c -> {
            if (!c.getDocId().equals(docId)) {
                throw new ClientException("Chunk " + c.getId() + " does not belong to document " + docId);
            }
        });
        List<String> targetIds = found.stream().map(KnowledgeChunkDO::getId).collect(Collectors.toList());

        if (CollUtil.isEmpty(targetIds)) {
            bizChangeLogContext.skip();
            return;
        }

        int enabledValue = enabled ? 1 : 0;
        List<KnowledgeChunkDO> needUpdateChunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunkDO>()
                        .in(KnowledgeChunkDO::getId, targetIds)
                        .ne(KnowledgeChunkDO::getEnabled, enabledValue)
        );
        List<String> needUpdateIds = needUpdateChunks.stream().map(KnowledgeChunkDO::getId).collect(Collectors.toList());

        if (CollUtil.isEmpty(needUpdateIds)) {
            throw new ClientException(enabled ? "All chunks are already enabled" : "All chunks are already disabled");
        }
        List<KnowledgeChunkDO> before = needUpdateChunks.stream()
                .map(each -> BeanUtil.copyProperties(each, KnowledgeChunkDO.class))
                .collect(Collectors.toList());

        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(documentDO.getKbId());
        String collectionName = kbDO.getCollectionName();

        if (enabled) {
            List<EmbeddedChunk> vectorChunks = embedPersisted(needUpdateChunks, vectorTargetResolver.resolve(kbDO));

            transactionOperations.executeWithoutResult(status -> {
                chunkMapper.update(
                        Wrappers.lambdaUpdate(KnowledgeChunkDO.class)
                                .in(KnowledgeChunkDO::getId, needUpdateIds)
                                .set(KnowledgeChunkDO::getEnabled, 1)
                                .set(KnowledgeChunkDO::getUpdatedBy, UserContext.getUsername())
                );
                vectorStoreService.indexDocumentChunks(collectionName, docId, vectorChunks);
            });
        } else {
            transactionOperations.executeWithoutResult(status -> {
                chunkMapper.update(
                        Wrappers.lambdaUpdate(KnowledgeChunkDO.class)
                                .in(KnowledgeChunkDO::getId, needUpdateIds)
                                .set(KnowledgeChunkDO::getEnabled, 0)
                                .set(KnowledgeChunkDO::getUpdatedBy, UserContext.getUsername())
                );
                vectorStoreService.deleteChunksByIds(collectionName, needUpdateIds);
            });
        }

        log.info("批量{}Chunk 成功, kbId={}, docId={}, count={}", enabled ? "启用" : "禁用",
                documentDO.getKbId(), docId, needUpdateIds.size());
        bizChangeLogContext.put(docId, before, chunkMapper.selectByIds(needUpdateIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabledByDocId(String docId, String kbId, boolean enabled) {
        int enabledValue = enabled ? 1 : 0;
        chunkMapper.update(
                Wrappers.lambdaUpdate(KnowledgeChunkDO.class)
                        .eq(KnowledgeChunkDO::getDocId, docId)
                        .set(KnowledgeChunkDO::getEnabled, enabledValue)
                        .set(KnowledgeChunkDO::getUpdatedBy, UserContext.getUsername())
        );
        log.info("根据文档ID更新所有Chunk启用状态, kbId={}, docId={}, enabled={}", kbId, docId, enabled);
    }

    @Override
    public List<EmbeddedChunk> embedPersistedChunks(String docId, VectorTarget target) {
        KnowledgeDocumentDO documentDO = documentMapper.selectById(docId);
        Assert.notNull(documentDO, () -> new ClientException("Document does not exist"));

        List<KnowledgeChunkDO> chunkDOList = chunkMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                        .eq(KnowledgeChunkDO::getDocId, docId)
                        .orderByAsc(KnowledgeChunkDO::getChunkIndex)
        );
        if (CollUtil.isEmpty(chunkDOList)) {
            return List.of();
        }
        return embedPersisted(chunkDOList, target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDocId(String docId) {
        if (docId == null) {
            return;
        }
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkDO>().eq(KnowledgeChunkDO::getDocId, docId));
    }

    // ==================== private methods ====================

    /**
     * The owning document must be enabled before a chunk can be enabled
     */
    private void validateDocumentEnabledForChunkEnable(KnowledgeDocumentDO documentDO, boolean enableChunk) {
        if (!enableChunk) {
            return;
        }
        if (!Integer.valueOf(1).equals(documentDO.getEnabled())) {
            throw new ClientException("Document is disabled, so the chunk cannot be enabled. Enable the document first");
        }
    }

    /**
     * Syncs one chunk to the vector store
     */
    private void syncChunkToVector(String collectionName, String docId, KnowledgeChunkDO chunkDO,
                                   VectorTarget target) {
        EmbeddedChunk chunk = embedPersisted(List.of(chunkDO), target).get(0);
        vectorStoreService.indexDocumentChunks(collectionName, docId, List.of(chunk));

        log.debug("同步 Chunk 到向量库成功, collectionName={}, docId={}, chunkId={}", collectionName, docId, chunkDO.getId());
    }

    /**
     * Deletes one chunk from the vector store
     */
    private void deleteChunkFromVector(String collectionName, String chunkId) {
        vectorStoreService.deleteChunkById(collectionName, chunkId);
        log.debug("从向量库删除 Chunk, collectionName={}, chunkId={}", collectionName, chunkId);
    }

    /**
     * Re-embeds a stored chunk. Embedding text is the copy in the database, and the chunk id stays the relational primary key
     * <p>
     * The only "row -> vector" entry in the system. See {@link ChunkAssembler#restore}
     */
    private List<EmbeddedChunk> embedPersisted(List<KnowledgeChunkDO> rows, VectorTarget target) {
        List<Chunk> chunks = rows.stream()
                .map(each -> ChunkAssembler.restore(each.getId(),
                        each.getChunkIndex() == null ? 0 : each.getChunkIndex(),
                        each.getContent(), each.getEmbeddingText()))
                .toList();
        return chunkEmbeddingService.embed(chunks, target);
    }

    private Integer resolveTokenCount(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return tokenCounterService.countTokens(content);
    }
}
