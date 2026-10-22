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
import com.nageoffer.ai.ragent.ingestion.controller.request.IngestionTaskCreateRequest;
import com.nageoffer.ai.ragent.ingestion.controller.vo.IngestionTaskNodeVO;
import com.nageoffer.ai.ragent.ingestion.controller.vo.IngestionTaskVO;
import com.nageoffer.ai.ragent.ingestion.domain.result.IngestionResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Data ingestion task service
 */
public interface IngestionTaskService {

    /**
     * Runs a data ingestion task
     *
     * @param request create request
     * @return ingestion result
     */
    IngestionResult execute(IngestionTaskCreateRequest request);

    /**
     * Uploads a file and runs an ingestion task
     *
     * @param pipelineId pipeline id
     * @param file       uploaded file
     * @return ingestion result
     */
    IngestionResult upload(String pipelineId, MultipartFile file);

    /**
     * Gets task detail
     *
     * @param taskId task id
     * @return task view
     */
    IngestionTaskVO get(String taskId);

    /**
     * Pages tasks
     *
     * @param page   page parameters
     * @param status status filter
     * @return page result
     */
    IPage<IngestionTaskVO> page(Page<IngestionTaskVO> page, String status);

    /**
     * Gets the task's node list
     *
     * @param taskId task id
     * @return node list
     */
    List<IngestionTaskNodeVO> listNodes(String taskId);
}
