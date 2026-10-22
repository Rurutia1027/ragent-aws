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

package com.nageoffer.ai.ragent.core.ingest;

/**
 * Document identity: plain data. Whether the bytes came from upload, a URL, or Feishu is before the kernel; the kernel does not know how they were fetched
 * <p>
 * docId is also passed to the parser to name image assets {@code assets/{docId}/...}. Omit it and the assets
 * land in a random directory, detached from the document
 *
 * @param docId    document id; decides asset ownership and where rows are stored
 * @param kbId     owning knowledge-base id; decides relational-store ownership
 * @param filename original file name, for type detection and traceability; may be empty (the delete path does not need it)
 */
public record DocumentRef(String docId, String kbId, String filename) {

    public DocumentRef {
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("docId cannot be empty");
        }
        if (kbId == null || kbId.isBlank()) {
            throw new IllegalArgumentException("kbId cannot be empty, docId=" + docId);
        }
    }

    /**
     * For the delete path: no file name is needed
     */
    public static DocumentRef of(String docId, String kbId) {
        return new DocumentRef(docId, kbId, null);
    }
}
