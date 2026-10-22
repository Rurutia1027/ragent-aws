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

package com.nageoffer.ai.ragent.infra.vlm;

/**
 * Vision-language model (VLM) access interface
 * <p>
 * The fourth model capability, alongside LLMService, EmbeddingService, and RerankService
 * Its only current use is image-to-text while ingesting the knowledge base: turn an image into a searchable description plus OCR of text in the image
 * Downstream answering stays a text model. The VLM is called only on the write path and is not on the chat hot path
 */
public interface VlmService {

    /**
     * Image to text: take image bytes and return the text the model generated (a description plus text in the image)
     * <p>
     * On failure throw {@link com.nageoffer.ai.ragent.infra.http.ModelClientException} directly. There is no fallback
     *
     * @param imageBytes      image bytes
     * @param mime            image MIME, such as image/png or image/jpeg
     * @param prompt          guiding prompt
     * @param maxOutputTokens output token cap, nullable (cost control)
     * @return description text returned by the model
     */
    String describeImage(byte[] imageBytes, String mime, String prompt, Integer maxOutputTokens);
}
