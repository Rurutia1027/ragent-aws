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
 * Common base of structured parse output: the in-memory form between a parser and ChunkerNode, alive only during parsing
 * <p>
 * sealed so the compiler exhausts the types; adding a Block type forces every switch to handle it. A Block describes
 * only the content itself. The outline path is accumulated into ChunkContext by HeadingHandler while walking, and
 * the markdown display text stored later is rendered by each chunker during splitting. No Jackson polymorphic
 * annotation: there is no serialization boundary anywhere on this path
 */
public sealed interface Block
        permits HeadingBlock, ParagraphBlock, TableBlock, HtmlTableBlock, ImageBlock, CodeBlock, ListBlock {

    /**
     * Provenance: file, page / sheet, bbox / cell range
     */
    Provenance provenance();
}
