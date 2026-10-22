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

package com.nageoffer.ai.ragent.core.chunk.text;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Boundary-aware text split: cut long text to the budget, with the cut on a natural boundary rather than an index
 * <p>
 * Walk back in this order: newline, then CJK sentence-ending punctuation, then English sentence-ending punctuation.
 * Also repair URLs broken across lines and join CJK soft wraps. A raw {@code substring} cuts sentences, URLs, and numbers in half
 */
public final class TextSplitter {

    private TextSplitter() {
    }

    /**
     * Splits text. Empty text returns an empty list
     *
     * @param overlapChars overlap in characters between adjacent pieces; also the maximum walk-back distance
     */
    public static List<String> split(String text, int maxChars, int overlapChars) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalized = normalize(text);
        if (normalized.length() <= maxChars) {
            return List.of(normalized);
        }

        int chunkSize = Math.max(1, maxChars);
        int overlap = chunkSize > 1 ? Math.min(Math.max(0, overlapChars), chunkSize - 1) : 0;
        int len = normalized.length();

        List<String> pieces = new ArrayList<>();
        int start = 0;
        int lastEnd = -1;
        while (start < len) {
            int targetEnd = Math.min(start + chunkSize, len);
            int end = adjustToBoundary(normalized, start, targetEnd, overlap);
            // Force progress: walking back too far repeats pieces or stalls
            if (end <= start || end <= lastEnd) {
                end = targetEnd;
            }
            String piece = normalized.substring(start, end);
            if (StringUtils.hasText(piece.strip())) {
                pieces.add(piece);
            }
            lastEnd = end;
            if (end >= len) {
                break;
            }
            int nextStart = Math.max(0, end - overlap);
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }
        return pieces;
    }

    /**
     * Walks back to a boundary: newline first, then CJK sentence-ending punctuation, then English sentence-ending punctuation
     * <p>
     * An English period counts only when whitespace or the end follows it; otherwise a dot in a URL host is cut.
     * Walk-back never exceeds overlap, so adjacent pieces do not repeat too much
     */
    private static int adjustToBoundary(String text, int start, int targetEnd, int overlap) {
        if (targetEnd <= start) {
            return targetEnd;
        }
        int maxLookback = Math.min(overlap, targetEnd - start);
        if (maxLookback <= 0) {
            return targetEnd;
        }

        for (int i = 0; i <= maxLookback; i++) {
            int pos = targetEnd - i - 1;
            if (pos <= start) {
                break;
            }
            if (text.charAt(pos) == '\n') {
                return pos + 1;
            }
        }
        for (int i = 0; i <= maxLookback; i++) {
            int pos = targetEnd - i - 1;
            if (pos <= start) {
                break;
            }
            char c = text.charAt(pos);
            if (c == '。' || c == '！' || c == '？') {
                return pos + 1;
            }
        }
        for (int i = 0; i <= maxLookback; i++) {
            int pos = targetEnd - i - 1;
            if (pos <= start) {
                break;
            }
            char c = text.charAt(pos);
            if (c == '.' || c == '!' || c == '?') {
                int next = pos + 1;
                if (next >= text.length() || Character.isWhitespace(text.charAt(next))) {
                    return next;
                }
            }
        }
        return targetEnd;
    }

    /**
     * Normalizes: drop {@code \r}, repair URLs split by a line break, and join a soft wrap in the middle of a CJK word
     * <p>
     * Never join across these two: a blank line (a paragraph break; joining would glue an image link to the following
     * heading), and a next line that looks like a list item ({@code 2.} / {@code 10)})
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String src = text.replace("\r", "");
        StringBuilder out = new StringBuilder(src.length());
        boolean inUrl = false;

        for (int i = 0; i < src.length(); i++) {
            if (!inUrl && looksLikeUrlStart(src, i)) {
                inUrl = true;
            }
            char c = src.charAt(i);

            if (inUrl) {
                if (Character.isWhitespace(c)) {
                    int j = i;
                    int newlineCount = 0;
                    while (j < src.length() && Character.isWhitespace(src.charAt(j))) {
                        if (src.charAt(j) == '\n') {
                            newlineCount++;
                        }
                        j++;
                    }
                    boolean sawNewline = newlineCount > 0;
                    boolean blankLine = newlineCount >= 2;
                    char prev = i > 0 ? src.charAt(i - 1) : 0;
                    char next = j < src.length() ? src.charAt(j) : 0;

                    if (sawNewline && !blankLine && next != 0 && shouldJoinBrokenUrl(prev, next, src, j)) {
                        i = j - 1;
                        continue;
                    }
                    out.append(src, i, j);
                    inUrl = false;
                    i = j - 1;
                    continue;
                }
                out.append(c);
                if (!isUrlChar(c) && !isCommonUrlPunct(c)) {
                    inUrl = false;
                }
                continue;
            }

            if (c == '\n') {
                char prev = i > 0 ? src.charAt(i - 1) : 0;
                char next = i + 1 < src.length() ? src.charAt(i + 1) : 0;
                // A CJK word split by a soft line break (halves separated by \\n) is joined back into one word
                if (isCjkWordChar(prev) && isCjkWordChar(next)) {
                    continue;
                }
                out.append('\n');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean shouldJoinBrokenUrl(char prev, char next, String s, int nextIndex) {
        if (isListItemStart(s, nextIndex)) {
            return false;
        }
        if (prev == '.' && Character.isLetter(next)) {
            return true;
        }
        if (prev == '/' || prev == '?' || prev == '&' || prev == '='
                || prev == '#' || prev == '%' || prev == '-' || prev == '_' || prev == ':') {
            return true;
        }
        return next == '/' || next == '?' || next == '&' || next == '=' || next == '#';
    }

    private static boolean isListItemStart(String s, int i) {
        int p = i;
        while (p < s.length() && (s.charAt(p) == ' ' || s.charAt(p) == '\t')) {
            p++;
        }
        int start = p;
        while (p < s.length() && Character.isDigit(s.charAt(p))) {
            p++;
        }
        if (p == start) {
            return false;
        }
        return p < s.length() && (s.charAt(p) == '.' || s.charAt(p) == '）' || s.charAt(p) == ')');
    }

    private static boolean looksLikeUrlStart(String s, int i) {
        return s.startsWith("http://", i) || s.startsWith("https://", i);
    }

    private static boolean isUrlChar(char c) {
        if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
            return true;
        }
        return c == '-' || c == '.' || c == '_' || c == '~'
                || c == ':' || c == '/' || c == '?' || c == '#'
                || c == '[' || c == ']' || c == '@'
                || c == '!' || c == '$' || c == '&' || c == '\''
                || c == '(' || c == ')' || c == '*' || c == '+'
                || c == ',' || c == ';' || c == '=' || c == '%';
    }

    private static boolean isCommonUrlPunct(char c) {
        return c == '.' || c == '/' || c == '?' || c == '&' || c == '=' || c == '-' || c == '_' || c == '%';
    }

    private static boolean isCjkWordChar(char c) {
        if (c == 0 || Character.isWhitespace(c)) {
            return false;
        }
        return isCjkOrFullWidthLetterOrDigit(c) && !isCjkPunctuation(c);
    }

    private static boolean isCjkOrFullWidthLetterOrDigit(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    private static boolean isCjkPunctuation(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || c == '。' || c == '，' || c == '、' || c == '；' || c == '：'
                || c == '！' || c == '？' || c == '（' || c == '）' || c == '【' || c == '】'
                || c == '《' || c == '》' || c == '“' || c == '”' || c == '‘' || c == '’';
    }
}
