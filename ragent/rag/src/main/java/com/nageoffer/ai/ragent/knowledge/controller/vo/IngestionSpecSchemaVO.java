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

package com.nageoffer.ai.ragent.knowledge.controller.vo;

import java.util.List;

/**
 * Form schema for the document-level ingestion config
 * <p>
 * Replaces the old "chunk strategy list". The strategy enum never made a difference on the real path: how to
 * cut is decided solely by document structure, and the user only controls the budget. The UI renders the form
 * from this schema, so the backend can add a parameter without a frontend change
 *
 * @param parseProfileLabel      field name of the profile options, issued with the option names so the two sides do not each store half and miss an edit
 * @param parseProfiles          selectable parse profiles
 * @param parseProfileExtensions extensions for which the profile actually differs; the UI must hide the profile option for every other format
 * @param budgetFields           chunk-budget field definitions
 * @param wholeDocumentSentinel  sentinel value for not splitting the document
 */
public record IngestionSpecSchemaVO(String parseProfileLabel,
                                    List<Option> parseProfiles,
                                    List<String> parseProfileExtensions,
                                    List<BudgetField> budgetFields,
                                    int wholeDocumentSentinel) {

    /**
     * @param value submitted value
     * @param label display name
     * @param hint  description
     */
    public record Option(String value, String label, String hint) {
    }

    /**
     * @param key            submitted key
     * @param label          display name
     * @param defaultValue   default value
     * @param min            minimum allowed; out of range is rejected at construction
     * @param max            maximum allowed; out of range is rejected at construction
     * @param recommendedMin lower bound of the suggested range; the UI shows only this span
     * @param recommendedMax upper bound of the suggested range
     * @param hint           one-line description, always shown
     * @param detail         tuning note. The UI puts it in a popover; laying the long notes of all four fields out side by side is a wall of text
     */
    public record BudgetField(String key, String label, int defaultValue, int min, int max,
                              int recommendedMin, int recommendedMax, String hint, String detail) {
    }
}
