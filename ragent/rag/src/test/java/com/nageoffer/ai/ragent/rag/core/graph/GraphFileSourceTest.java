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

package com.nageoffer.ai.ragent.rag.core.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GraphFileSourceTest {

    @Test
    @DisplayName("编码与解析互为逆运算")
    void roundTrip() {
        String filePath = GraphFileSource.encode("kb-finance", "1954071234567890123");

        GraphFileSource source = GraphFileSource.parse(filePath);

        assertEquals("kb-finance", source.collectionName());
        assertEquals("1954071234567890123", source.docId());
    }

    @Test
    @DisplayName("互为前缀的库名不互相误归属")
    void prefixOverlappingCollectionsDoNotCrossMatch() {
        // Regression: kb and kb_hr may legally coexist. The old contains(collectionName + "_") assigned kb_hr documents to kb,
        // mixing evidence on the read side and deleting kb_hr graph data on the delete side
        GraphFileSource source = GraphFileSource.parse("kb_hr_1954071234567890123");

        assertEquals("kb_hr", source.collectionName());
        assertEquals("1954071234567890123", source.docId());
    }

    @Test
    @DisplayName("库名可含下划线与数字段，从右锚定仍唯一还原")
    void underscoreAndDigitSegmentsInCollectionName() {
        assertEquals(new GraphFileSource("kb_2024_v2", "98765"),
                GraphFileSource.parse(GraphFileSource.encode("kb_2024_v2", "98765")));
        // When the collection name ends in a numeric segment, that last numeric segment is the docId
        assertEquals(new GraphFileSource("kb_7", "123"),
                GraphFileSource.parse("kb_7_123"));
    }

    @Test
    @DisplayName("容忍服务端的目录前缀与扩展名修饰")
    void toleratesServerSideDecorations() {
        assertEquals(new GraphFileSource("kb", "123"),
                GraphFileSource.parse("/data/inputs/kb_123.txt"));
        assertEquals(new GraphFileSource("kb", "123"),
                GraphFileSource.parse("kb_123.tar.gz"));
    }

    @Test
    @DisplayName("不符合编码的 file_path 一律解析为 null")
    void nonConformingPathsParseToNull() {
        assertNull(GraphFileSource.parse(null));
        assertNull(GraphFileSource.parse("  "));
        assertNull(GraphFileSource.parse("readme.txt"));
        assertNull(GraphFileSource.parse("kb_"));
        assertNull(GraphFileSource.parse("123"));
        // A number not in the last segment must not fall back to "any digit run", or a wrong owner is worse than no owner
        assertNull(GraphFileSource.parse("abc123def"));
    }
}
