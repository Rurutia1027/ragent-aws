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

package com.nageoffer.ai.ragent.infra.embedding;

import com.nageoffer.ai.ragent.infra.model.ModelTarget;

import java.util.List;

/**
 * Text embedding client
 * Converts text to vectors, for one text or a batch
 */
public interface EmbeddingClient {

    /**
     * Embedding provider name
     *
     * @return provider id
     */
    String provider();

    /**
     * Embed a single text
     *
     * @param text   text to embed
     * @param target target model configuration
     * @return vector for the text, as a list of floats
     */
    List<Float> embed(String text, ModelTarget target);

    /**
     * Embed a batch of texts
     *
     * @param texts  texts to embed
     * @param target target model configuration
     * @return one vector per text, each a list of floats
     */
    List<List<Float>> embedBatch(List<String> texts, ModelTarget target);
}
