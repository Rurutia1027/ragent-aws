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
 * Raw HTML table Block. MinerU's layout parse emits {@code <table>} rather than a pipe table, and HtmlTableChunker splits it by row
 * <p>
 * Deliberately not split into {@link TableBlock} headers/rows: merged cells, newlines inside cells, and formula
 * fragments are distorted when expanded into a 2-D table. Keeping the original HTML lets the display side render it
 *
 * @param html complete table HTML, starting with {@code <table}
 */
public record HtmlTableBlock(
        Provenance provenance,
        String html
) implements Block {
}
