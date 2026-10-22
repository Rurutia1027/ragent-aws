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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Document parser type
 */
@Getter
@RequiredArgsConstructor
public enum ParserType {

    /**
     * Tika parser (basic formats such as text)
     */
    TIKA("Tika"),

    /**
     * Markdown parser
     */
    MARKDOWN("Markdown"),

    /**
     * Apache POI Excel parser (merged cells / multi-row headers / hyperlinks)
     */
    EXCEL_POI("ExcelPoi"),

    /**
     * CSV parser (charset auto-detect + RFC4180; produces one key-value table)
     */
    CSV("Csv"),

    /**
     * MinerU SaaS parser (PDF / Word / PPT / Excel, including tables, images, and layout)
     */
    MINERU("MinerU"),

    /**
     * Image parser (PNG / JPG; VLM image-to-text, and the original image is stored)
     */
    IMAGE("Image");

    /**
     * Parser type name
     */
    private final String type;
}
