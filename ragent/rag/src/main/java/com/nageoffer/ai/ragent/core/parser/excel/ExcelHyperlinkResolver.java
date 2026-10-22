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

package com.nageoffer.ai.ragent.core.parser.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Hyperlink;

/**
 * Excel hyperlink resolver
 * <p>
 * Meets the hard requirement of "a link inside the text": a cell's visible text and its URL are separate (the URL
 * is cell metadata). The hyperlink must be read explicitly and joined into the markdown inline form {@code [text](url)}
 * <p>
 * OCR / layout tools such as MinerU cannot see this metadata. That is the core reason Excel must go through POI
 */
public final class ExcelHyperlinkResolver {

    private ExcelHyperlinkResolver() {
    }

    /**
     * Wraps cell text as a markdown inline hyperlink
     *
     * @param cellText visible cell text (already formatted by ExcelValueFormatter)
     * @param cell     cell instance, used to look up the hyperlink; may be null
     * @return {@code [cellText](url)} when the cell has a non-empty hyperlink; otherwise cellText unchanged
     */
    public static String wrap(String cellText, Cell cell) {
        if (cell == null) {
            return cellText == null ? "" : cellText;
        }
        Hyperlink hyperlink = cell.getHyperlink();
        if (hyperlink == null) {
            return cellText == null ? "" : cellText;
        }
        String url = hyperlink.getAddress();
        if (url == null || url.isBlank()) {
            return cellText == null ? "" : cellText;
        }
        String visible = (cellText == null || cellText.isEmpty()) ? hyperlink.getLabel() : cellText;
        if (visible == null || visible.isEmpty()) {
            visible = url;
        }
        return "[" + visible + "](" + url + ")";
    }
}
