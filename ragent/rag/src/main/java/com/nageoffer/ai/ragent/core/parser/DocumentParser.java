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

package com.nageoffer.ai.ragent.core.parser;

import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.registry.ParseProfile;

import java.util.Map;
import java.util.Set;

/**
 * Unified document-parser interface. The core is {@link #parseStructured}, which produces a {@link ParsedDocument} containing a Block list
 * <p>
 * A parser claims (MIME x profile) explicitly through {@link #supportedMimeTypes()}. {@code ParserRegistry}
 * builds the table at startup, and a key conflict fails startup
 */
public interface DocumentParser {

    /**
     * Parser type id. Values are in {@link ParserType}
     */
    String getParserType();

    /**
     * Structured parse: an ordered Block list (sections, paragraphs, tables, images, and so on). mimeType and options may be empty
     */
    ParsedDocument parseStructured(byte[] content, String mimeType, Map<String, Object> options);

    /**
     * Claim list: profile -> the MIME set claimed at that profile. Must not be empty
     * <p>
     * MIME types are lowercase. {@code type/*} wildcards are supported, and an exact key beats a wildcard. If the
     * requested profile has no registration, fall back to the global fallback profile {@link ParseProfile#FAST}, so a
     * parser declares a profile only when that profile has its own implementation. Excel's FAST profile, for example,
     * uses POI for a quick key-value parse, and only FIDELITY is handed to MinerU for layout parsing
     */
    Map<ParseProfile, Set<String>> supportedMimeTypes();
}
