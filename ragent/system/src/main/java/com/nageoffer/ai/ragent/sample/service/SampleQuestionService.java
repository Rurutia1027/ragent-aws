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

package com.nageoffer.ai.ragent.sample.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nageoffer.ai.ragent.sample.controller.request.SampleQuestionCreateRequest;
import com.nageoffer.ai.ragent.sample.controller.request.SampleQuestionPageRequest;
import com.nageoffer.ai.ragent.sample.controller.request.SampleQuestionUpdateRequest;
import com.nageoffer.ai.ragent.sample.controller.vo.SampleQuestionVO;

import java.util.List;

public interface SampleQuestionService {

    /**
     * Create a sample question.
     */
    String create(SampleQuestionCreateRequest requestParam);

    /**
     * Update a sample question.
     */
    void update(String id, SampleQuestionUpdateRequest requestParam);

    /**
     * Delete a sample question.
     */
    void delete(String id);

    /**
     * Get a sample question by id.
     */
    SampleQuestionVO queryById(String id);

    /**
     * Page through sample questions.
     */
    IPage<SampleQuestionVO> pageQuery(SampleQuestionPageRequest requestParam);

    /**
     * Return a random list of sample questions.
     *
     * @param limit requested count, clamped to the allowed range
     */
    List<SampleQuestionVO> listRandomQuestions(int limit);
}
