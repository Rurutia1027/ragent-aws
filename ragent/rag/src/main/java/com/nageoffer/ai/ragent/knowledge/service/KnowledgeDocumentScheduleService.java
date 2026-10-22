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

package com.nageoffer.ai.ragent.knowledge.service;

import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;

/**
 * Scheduled-task service for knowledge-base documents
 */
public interface KnowledgeDocumentScheduleService {

    /**
     * Creates or updates the schedule row from the document
     *
     * @param documentDO document entity
     */
    void upsertSchedule(KnowledgeDocumentDO documentDO);

    /**
     * Syncs the schedule when a document is enabled or disabled (updates an existing task only)
     *
     * @param documentDO document entity
     */
    void syncScheduleIfExists(KnowledgeDocumentDO documentDO);

    /**
     * Deletes the schedule and its execution records for a document
     *
     * @param docId document id
     */
    void deleteByDocId(String docId);
}
