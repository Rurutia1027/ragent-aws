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

package com.nageoffer.ai.ragent.core.parser.model;

/**
 * Image Block. ImageChunker renders it as an atomic chunk {@code ![caption](url)}. A broken image link fails frontend rendering
 *
 * @param description VLM image-to-text result, used both for embedding retrieval and for the LLM answer. Null for sources such as MinerU that do not produce image-to-text
 */
public record ImageBlock(
        Provenance provenance,
        AssetRef asset,
        String caption,
        String altText,
        String description
) implements Block {

    /**
     * Shape used by sources that do not produce image-to-text (MinerU / Excel and others). description is left empty
     */
    public ImageBlock(Provenance provenance, AssetRef asset, String caption, String altText) {
        this(provenance, asset, caption, altText, null);
    }
}
