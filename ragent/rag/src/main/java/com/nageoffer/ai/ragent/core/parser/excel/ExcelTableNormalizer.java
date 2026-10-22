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
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel table normalizer (simple key-value)
 * <p>
 * Turns one POI Sheet into a single clean (headers, rows) grid. Only the generic cleanup for a regular single table:
 * <ul>
 *   <li>Expand merged cells: copy the top-left value into every cell of the region (friendly to self-contained row chunks)</li>
 *   <li>Flatten multi-row headers: join the first N rows into one header, column names joined by a separator (for example "finance|revenue")</li>
 *   <li>Keep hyperlinks: wrap cell text in {@code [text](url)}</li>
 *   <li>Formula fallback: through {@link ExcelValueFormatter}</li>
 *   <li>Skip all-empty rows and trailing all-empty columns</li>
 * </ul>
 * <p>
 * <b>No longer done here</b>: splitting multiple table regions, detecting document or section titles, folding
 * horizontally repeated columns, and similar layout heuristics. Excel with that kind of layout should be routed
 * to MinerU by the layer above. POI is responsible only for one regular table per sheet
 */
public final class ExcelTableNormalizer {

    /**
     * Separator used when flattening a multi-row header
     */
    public static final String HEADER_SEPARATOR = "|";

    /**
     * Wrap marker for a struck-through cell (soft-delete convention: wrap the original value in the GFM strikethrough {@code ~~value~~}, keeping the text and marking it explicitly)
     */
    private static final String STRIKETHROUGH_WRAP = "~~";

    private ExcelTableNormalizer() {
    }

    /**
     * Normalization result
     *
     * @param headers flattened column names (length equals the number of effective columns)
     * @param rows    data rows (aligned with headers; all-empty rows already skipped)
     */
    public record NormalizedTable(
            List<String> headers,
            List<List<String>> rows
    ) {
        public boolean isEmpty() {
            return headers.isEmpty() && rows.isEmpty();
        }

        static NormalizedTable empty() {
            return new NormalizedTable(List.of(), List.of());
        }
    }

    /**
     * Normalizes a sheet into one table: the first headerRows rows are the header, the rest are data rows
     *
     * @param sheet      POI sheet
     * @param formatter  DataFormatter instance (not thread-safe; the caller owns it)
     * @param evaluator  formula evaluator; may be null
     * @param headerRows number of header rows, {@code >= 1}
     * @return normalized result; an empty sheet returns an empty table
     */
    public static NormalizedTable normalize(Sheet sheet,
                                            DataFormatter formatter,
                                            FormulaEvaluator evaluator,
                                            int headerRows) {
        if (headerRows < 1) {
            throw new IllegalArgumentException("headerRows must be >= 1, got " + headerRows);
        }

        int lastRowNum = sheet.getLastRowNum();
        if (lastRowNum < 0) {
            return NormalizedTable.empty();
        }

        int maxCol = computeMaxColumns(sheet, lastRowNum);
        if (maxCol == 0) {
            return NormalizedTable.empty();
        }

        // Step 1: read the sheet into a 2-D grid (hyperlink wrap and formula fallback already applied)
        String[][] grid = readGrid(sheet, lastRowNum, maxCol, formatter, evaluator);

        // Step 2: expand merged cells (fill in place on the grid)
        expandMergedRegions(grid, sheet.getMergedRegions(), lastRowNum, maxCol);

        // Step 3: drop all-empty columns (columns empty through header and data, in the middle or at the end)
        int[] cols = selectNonEmptyColumns(grid, 0, lastRowNum, maxCol);
        if (cols.length == 0) {
            return NormalizedTable.empty();
        }

        // Step 4: flatten the first headerRows rows into the header, and collect the rest as data rows
        int effectiveHeaderRows = Math.min(headerRows, lastRowNum + 1);
        List<String> headers = flattenHeaders(grid, 0, effectiveHeaderRows, cols);
        List<List<String>> rows = effectiveHeaderRows <= lastRowNum
                ? collectDataRows(grid, effectiveHeaderRows, lastRowNum, cols)
                : List.of();
        return new NormalizedTable(headers, rows);
    }

    /**
     * Computes the maximum column count in the sheet, across all rows
     */
    private static int computeMaxColumns(Sheet sheet, int lastRowNum) {
        int maxCol = 0;
        for (int r = 0; r <= lastRowNum; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int lastCellNum = row.getLastCellNum();
            if (lastCellNum > maxCol) {
                maxCol = lastCellNum;
            }
        }
        return maxCol;
    }

    /**
     * Selects columns that are not all empty: column indexes (ascending) with at least one non-empty cell in [startRow, endRow]
     * <p>
     * An all-empty column (empty through both header and data) carries no information and is dropped, whether it is
     * a trailing empty column or an empty column between data columns. A column with an empty header but real data is kept (the data itself matters)
     */
    private static int[] selectNonEmptyColumns(String[][] grid, int startRow, int endRow, int maxCol) {
        List<Integer> kept = new ArrayList<>(maxCol);
        for (int c = 0; c < maxCol; c++) {
            boolean hasValue = false;
            for (int r = startRow; r <= endRow; r++) {
                String v = grid[r][c];
                if (v != null && !v.isEmpty()) {
                    hasValue = true;
                    break;
                }
            }
            if (hasValue) {
                kept.add(c);
            }
        }
        int[] cols = new int[kept.size()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = kept.get(i);
        }
        return cols;
    }

    /**
     * Reads the sheet into a 2-D array, applying hyperlinks and formula fallback
     * <p>
     * Uses {@link Row.MissingCellPolicy#RETURN_NULL_AND_BLANK}: a missing cell returns null, while a BLANK cell still
     * returns a cell instance (it may carry a hyperlink). Empty text plus a hyperlink then resolves correctly
     */
    private static String[][] readGrid(Sheet sheet, int lastRowNum, int maxCol,
                                       DataFormatter formatter, FormulaEvaluator evaluator) {
        String[][] grid = new String[lastRowNum + 1][maxCol];
        for (int r = 0; r <= lastRowNum; r++) {
            Row row = sheet.getRow(r);
            for (int c = 0; c < maxCol; c++) {
                String value = "";
                if (row != null) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
                    if (cell != null) {
                        String formatted = ExcelValueFormatter.format(cell, formatter, evaluator);
                        value = ExcelHyperlinkResolver.wrap(formatted, cell);
                        // Soft-delete convention: a non-empty struck-through cell is wrapped as ~~value~~
                        if (!value.isEmpty() && ExcelValueFormatter.isStrikethrough(cell)) {
                            value = STRIKETHROUGH_WRAP + value + STRIKETHROUGH_WRAP;
                        }
                    }
                }
                grid[r][c] = value;
            }
        }
        return grid;
    }

    /**
     * Copies the top-left value of a merged region into every cell position in that region
     */
    private static void expandMergedRegions(String[][] grid,
                                            List<CellRangeAddress> mergedRegions,
                                            int lastRowNum, int maxCol) {
        if (mergedRegions == null || mergedRegions.isEmpty()) {
            return;
        }
        for (CellRangeAddress region : mergedRegions) {
            int firstRow = region.getFirstRow();
            int firstCol = region.getFirstColumn();
            if (firstRow < 0 || firstRow > lastRowNum || firstCol < 0 || firstCol >= maxCol) {
                continue;
            }
            String value = grid[firstRow][firstCol];
            if (value == null || value.isEmpty()) {
                continue;
            }
            int rEnd = Math.min(region.getLastRow(), lastRowNum);
            int cEnd = Math.min(region.getLastColumn(), maxCol - 1);
            for (int r = firstRow; r <= rEnd; r++) {
                for (int c = firstCol; c <= cEnd; c++) {
                    grid[r][c] = value;
                }
            }
        }
    }

    /**
     * Flattens the first N rows into a single header row: adjacent identical values are merged (avoids duplicates after expanding merged cells)
     */
    private static List<String> flattenHeaders(String[][] grid, int startRow, int headerRows, int[] cols) {
        List<String> headers = new ArrayList<>(cols.length);
        for (int c : cols) {
            StringBuilder sb = new StringBuilder();
            String prev = null;
            for (int r = startRow; r < startRow + headerRows; r++) {
                String v = grid[r][c];
                if (v == null || v.isEmpty()) {
                    continue;
                }
                if (v.equals(prev)) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append(HEADER_SEPARATOR);
                }
                sb.append(v);
                prev = v;
            }
            headers.add(sb.toString());
        }
        return headers;
    }

    /**
     * Collects data rows (all-empty rows are skipped)
     */
    private static List<List<String>> collectDataRows(String[][] grid, int startRow,
                                                      int endRow, int[] cols) {
        List<List<String>> rows = new ArrayList<>();
        for (int r = startRow; r <= endRow; r++) {
            List<String> rowValues = new ArrayList<>(cols.length);
            boolean allEmpty = true;
            for (int c : cols) {
                String v = grid[r][c];
                if (v != null && !v.isEmpty()) {
                    allEmpty = false;
                }
                rowValues.add(v == null ? "" : v);
            }
            if (!allEmpty) {
                rows.add(rowValues);
            }
        }
        return rows;
    }
}
