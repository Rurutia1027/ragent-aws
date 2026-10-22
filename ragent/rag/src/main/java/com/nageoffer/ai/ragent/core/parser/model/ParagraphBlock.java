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
 * Paragraph Block. ParagraphChunker splits it by token and may merge across paragraphs up to the target length, but not across a heading
 *
 * @param text paragraph text. Links, images, and inline code markers are kept; emphasis markers are dropped.
 *            Non-table HTML embedded in the markdown also lands here as-is (tables go to {@link HtmlTableBlock}), so do not assume it is plain text
 */
public record ParagraphBlock(
        Provenance provenance,
        String text
) implements Block {
}
