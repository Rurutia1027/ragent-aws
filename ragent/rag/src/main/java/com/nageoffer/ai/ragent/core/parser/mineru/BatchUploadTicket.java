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

package com.nageoffer.ai.ragent.core.parser.mineru;

/**
 * Credential returned by MinerU's apply-for-upload-URL API (single file)
 * <p>
 * {@link MinerUClient#requestUpload} returns it: batchId is used for later polling, and uploadUrl is a presigned
 * PUT link on MinerU OSS (valid 24h). PUT the file bytes there directly
 *
 * @param batchId   batch_id assigned by MinerU; the credential for polling and download
 * @param uploadUrl target URL for the file upload. PUT the raw bytes; no auth header
 */
public record BatchUploadTicket(
        String batchId,
        String uploadUrl
) {
}
