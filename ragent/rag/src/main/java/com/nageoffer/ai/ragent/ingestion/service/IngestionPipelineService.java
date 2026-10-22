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

package com.nageoffer.ai.ragent.ingestion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.ingestion.controller.request.IngestionPipelineCreateRequest;
import com.nageoffer.ai.ragent.ingestion.controller.request.IngestionPipelineUpdateRequest;
import com.nageoffer.ai.ragent.ingestion.controller.vo.IngestionPipelineVO;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.PipelineDefinition;

/**
 * Data cleaning pipeline service
 */
public interface IngestionPipelineService {

    /**
     * Creates a pipeline
     *
     * @param request create request
     * @return pipeline view
     */
    IngestionPipelineVO create(IngestionPipelineCreateRequest request);

    /**
     * Updates a pipeline
     *
     * @param pipelineId pipeline id
     * @param request    update request
     * @return pipeline view
     */
    IngestionPipelineVO update(String pipelineId, IngestionPipelineUpdateRequest request);

    /**
     * Gets pipeline detail
     *
     * @param pipelineId pipeline id
     * @return pipeline view
     */
    IngestionPipelineVO get(String pipelineId);

    /**
     * Pages pipelines
     *
     * @param page    page parameters
     * @param keyword keyword
     * @return page result
     */
    IPage<IngestionPipelineVO> page(Page<IngestionPipelineVO> page, String keyword);

    /**
     * Deletes a pipeline
     *
     * @param pipelineId pipeline id
     */
    void delete(String pipelineId);

    /**
     * Gets the pipeline definition
     *
     * @param pipelineId pipeline id
     * @return pipeline definition
     */
    PipelineDefinition getDefinition(String pipelineId);
}
