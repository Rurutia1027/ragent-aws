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
 * Request body for applying for a MinerU upload URL (reduced form, single file)
 * <p>
 * Uses MinerU's official "local file batch upload and parse": submit file metadata only, with no url.
 * The real request JSON is built inside {@link MinerUClient#requestUpload}. Field names follow MinerU:
 * <pre>
 * {
 *   "enable_formula": true,
 *   "enable_table":   true,
 *   "language":       "ch",
 *   "files": [
 *     {
 *       "name":     "xxx.pdf",
 *       "is_ocr":   false,
 *       "data_id":  "doc-uuid"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @param fileName      file name; must carry the correct extension, which MinerU uses to detect the format
 * @param dataId        caller business id, echoed back on {@link MinerUStatus}
 * @param isOcr         whether to force OCR
 * @param enableTable   whether to extract tables
 * @param enableFormula whether to extract formulas
 * @param language      language code, following MinerU (PaddleOCR), such as ch / en / chinese_cht
 */
public record BatchSubmitRequest(
        String fileName,
        String dataId,
        boolean isOcr,
        boolean enableTable,
        boolean enableFormula,
        String language
) {
}
