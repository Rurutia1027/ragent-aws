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

package com.nageoffer.ai.ragent.rag.core.vector;

import com.nageoffer.ai.ragent.rag.config.RAGDefaultProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "milvus", matchIfMissing = true)
public class MilvusVectorStoreAdmin implements VectorStoreAdmin {

    private final MilvusClientV2 milvusClient;
    private final RAGDefaultProperties ragDefaultProperties;

    @Override
    public void ensureVectorSpace(VectorSpaceSpec spec) {
        // All of Milvus shares one physical collection. Ensure it exists, idempotently. Knowledge bases are distinguished by the collection_name scalar field
        String sharedCollection = ragDefaultProperties.getCollectionName();
        boolean exists = Boolean.TRUE.equals(milvusClient.hasCollection(
                HasCollectionReq.builder().collectionName(sharedCollection).build()
        ));
        if (exists) {
            return;
        }

        List<CreateCollectionReq.FieldSchema> fieldSchemaList = new ArrayList<>();

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("id")
                        .dataType(DataType.VarChar)
                        // chunkId is a snowflake primary key (at most 19 digits), aligned with PG t_knowledge_vector.id VARCHAR(20)
                        .maxLength(20)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build()
        );

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("collection_name")
                        .dataType(DataType.VarChar)
                        .maxLength(64)
                        .build()
        );

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("content")
                        .dataType(DataType.VarChar)
                        .maxLength(65535)
                        .build()
        );

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("metadata")
                        .dataType(DataType.JSON)
                        .build()
        );

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("embedding")
                        .dataType(DataType.FloatVector)
                        .dimension(ragDefaultProperties.getDimension())
                        .build()
        );

        CreateCollectionReq.CollectionSchema collectionSchema = CreateCollectionReq.CollectionSchema
                .builder()
                .fieldSchemaList(fieldSchemaList)
                .build();

        IndexParam hnswIndex = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .indexName("embedding")
                .extraParams(Map.of(
                        "M", "48",
                        "efConstruction", "200",
                        "mmap.enabled", "false"
                ))
                .build();

        // Every search on the shared collection is "collection_name filter + ANN". Build an inverted index on the scalar field so a large store does not scan every scalar
        IndexParam collectionNameIndex = IndexParam.builder()
                .fieldName("collection_name")
                .indexType(IndexParam.IndexType.INVERTED)
                .indexName("collection_name")
                .build();

        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(sharedCollection)
                .collectionSchema(collectionSchema)
                .primaryFieldName("id")
                .vectorFieldName("embedding")
                .metricType(ragDefaultProperties.getMetricType())
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .indexParams(List.of(hnswIndex, collectionNameIndex))
                .description("RAG 共享向量存储")
                .build();

        milvusClient.createCollection(createReq);
        log.info("已创建 Milvus 共享 collection: {}", sharedCollection);
    }

    @Override
    public boolean vectorSpaceExists(VectorSpaceId spaceId) {
        // Under the shared-collection model, existence means the shared collection has been created (the logical name passed in is ignored)
        return Boolean.TRUE.equals(milvusClient.hasCollection(
                HasCollectionReq.builder().collectionName(ragDefaultProperties.getCollectionName()).build()
        ));
    }

    @Override
    public void dropVectorSpace(String collectionName) {
        // Shared-collection model: delete that knowledge base's rows by the collection_name scalar, rather than dropping the whole collection
        String filter = "collection_name == \"" + collectionName + "\"";
        DeleteResp resp = milvusClient.delete(DeleteReq.builder()
                .collectionName(ragDefaultProperties.getCollectionName())
                .filter(filter)
                .build());
        log.info("已删除 collection_name={} 的向量行，deleteCnt={}", collectionName, resp.getDeleteCnt());
    }
}
