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
 * Block provenance, stored in chunk metadata so debugging can locate the position in the original document
 * <p>
 * sheetName is only a trace marker and does not join the embedding text: the Excel parser emits a separate HeadingBlock for the sheet name, which goes through the outline path
 *
 * @param sourceFile original file id, a file id or a file name
 * @param sheetName  Excel sheet name; null for a non-Excel source
 */
public record Provenance(String sourceFile, String sheetName) {

    public static Provenance ofFile(String sourceFile) {
        return new Provenance(sourceFile, null);
    }

    public static Provenance ofExcelCell(String sourceFile, String sheetName) {
        return new Provenance(sourceFile, sheetName);
    }
}
