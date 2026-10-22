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

package com.nageoffer.ai.ragent.core.chunk.blockaware;

import com.nageoffer.ai.ragent.core.chunk.model.ChunkBudget;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkDraft;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkMetadata;
import com.nageoffer.ai.ragent.core.parser.model.AssetRef;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Chunk packer: a section is "between two adjacent headings", and sections are packed into chunks by size
 * <p>
 * Cuts fall only on section boundaries, so there is no chunk-level overlap: overlap exists to keep an answer
 * from being cut, and a section boundary has no cut sentence. The only exception is an in-section split when
 * the whole section exceeds the tolerance cap. Overlap at that layer is {@code TextSplitter}'s job while it cuts paragraphs
 */
@Component
public class ChunkPacker {

    /**
     * Separator between chunks when merging, preserving paragraph / list boundaries
     */
    private static final String SEPARATOR = "\n\n";

    /**
     * Minimum chunk size is this fraction of the chunk size. Below it, the text is too small to stand alone; fold it into the next section instead
     */
    private static final int MIN_CHARS_DIVISOR = 4;

    /**
     * Packs into chunks
     * <p>
     * A section that as a whole stays within the tolerance cap is atomic: either fold the whole section into the
     * current chunk, or let the whole section be its own chunk. Only a section that exceeds the cap is cut by size inside the section
     *
     * @param drafts ordered drafts produced by splitting
     * @param budget chunk budget; the merge cap is {@link ChunkBudget#maxChars()}
     */
    public List<ChunkDraft> pack(List<ChunkDraft> drafts, ChunkBudget budget) {
        if (drafts == null || drafts.size() <= 1) {
            return drafts == null ? List.of() : drafts;
        }

        int maxChars = budget.maxChars();
        int minChars = Math.max(1, maxChars / MIN_CHARS_DIVISOR);
        List<ChunkDraft> result = new ArrayList<>();
        List<ChunkDraft> buffer = new ArrayList<>();

        for (List<ChunkDraft> section : splitSections(drafts)) {
            int sectionLen = totalLength(section);
            if (sectionLen > budget.toleranceChars()) {
                buffer = packWithin(buffer, section, budget, minChars, result);
                continue;
            }
            if (!buffer.isEmpty() && breakBefore(totalLength(buffer), sectionLen, minChars, budget)) {
                flush(buffer, result, budget, minChars);
                buffer = new ArrayList<>();
            }
            buffer.addAll(section);
            // An atomic section may itself exceed the budget. Landing in an empty buffer makes one over-budget chunk; it must not, and cannot, be split further
            if (totalLength(buffer) > maxChars) {
                flush(buffer, result, budget, minChars);
                buffer = new ArrayList<>();
            }
        }
        flush(buffer, result, budget, minChars);
        return result;
    }

    /**
     * Whether to break on reaching the next section boundary
     * <p>
     * minChars is the floor and maxChars is the target; the two jobs do not share one threshold. Treating "break
     * once minChars is reached" as the break rule turns the floor into the de facto target, so a document dense
     * with headings, even with 1024, only yields chunks around 300
     */
    private static boolean breakBefore(int bufferLen, int sectionLen, int minChars, ChunkBudget budget) {
        // Not yet one chunk: accept the merge even if it exceeds the budget. The tolerance cap is the real floor
        if (bufferLen < minChars) {
            return bufferLen + SEPARATOR.length() + sectionLen > budget.toleranceChars();
        }
        // If it fits, keep packing. A section boundary is only a candidate break; break for real only when it does not fit
        return bufferLen + SEPARATOR.length() + sectionLen > budget.maxChars();
    }

    /**
     * Splits into sections on headings. A heading starts a section; stray chunks before the first heading form a section of their own
     */
    private static List<List<ChunkDraft>> splitSections(List<ChunkDraft> drafts) {
        List<List<ChunkDraft>> sections = new ArrayList<>();
        List<ChunkDraft> current = new ArrayList<>();
        for (ChunkDraft draft : drafts) {
            if (draft.heading() && !current.isEmpty()) {
                sections.add(current);
                current = new ArrayList<>();
            }
            current.add(draft);
        }
        if (!current.isEmpty()) {
            sections.add(current);
        }
        return sections;
    }

    /**
     * In-section split: when a section blows past the tolerance cap, greedily accumulate drafts and return the unflushed remainder
     * <p>
     * The remainder goes back to the caller instead of being flushed here, so it can merge with the next section.
     * Otherwise the tail of a section always becomes its own chunk
     *
     * @param carried buffer left by the previous section; it joins this section's accumulation
     */
    private static List<ChunkDraft> packWithin(List<ChunkDraft> carried, List<ChunkDraft> section,
                                               ChunkBudget budget, int minChars, List<ChunkDraft> result) {
        int maxChars = budget.maxChars();
        List<ChunkDraft> buffer = new ArrayList<>(carried);
        for (ChunkDraft draft : section) {
            int addLen = contentLength(draft);
            // Already at the chunk-size cap, or already one piece of a split Block: flush as-is, and only carry the immediately preceding lead-in along
            if (draft.piece() || addLen >= maxChars) {
                List<ChunkDraft> leadIn = pollLeadIn(buffer, draft, budget);
                flush(buffer, result, budget, minChars);
                List<ChunkDraft> parts = new ArrayList<>(leadIn);
                parts.add(draft);
                result.add(parts.size() == 1 ? draft : merge(parts));
                buffer = new ArrayList<>();
                continue;
            }
            if (!buffer.isEmpty() && totalLength(buffer) + SEPARATOR.length() + addLen > maxChars) {
                flush(buffer, result, budget, minChars);
                buffer = new ArrayList<>();
            }
            buffer.add(draft);
        }
        return buffer;
    }

    /**
     * Takes the lead-in that can be merged into a large chunk. Taken drafts are removed from the buffer; returns an empty list when there is none
     * <p>
     * A table note such as "deposit unit is yuan", or the description of a code block, is written in the previous
     * paragraph. Leaving it as an orphan chunk separates the retrieval entry from the content
     */
    private static List<ChunkDraft> pollLeadIn(List<ChunkDraft> buffer, ChunkDraft target, ChunkBudget budget) {
        int limit = budget.maxChars();
        int taken = 0;
        int from = buffer.size();
        while (from > 0) {
            int next = taken + SEPARATOR.length() + contentLength(buffer.get(from - 1));
            if (next > limit) {
                break;
            }
            taken = next;
            from--;
        }
        if (from == buffer.size() || contentLength(target) + taken > budget.toleranceChars()) {
            return List.of();
        }
        List<ChunkDraft> leadIn = new ArrayList<>(buffer.subList(from, buffer.size()));
        buffer.subList(from, buffer.size()).clear();
        return leadIn;
    }

    /**
     * Flushes the buffer. A remainder under the minimum chunk size is merged back into the previous chunk
     * <p>
     * This is the backstop for "do not emit a chunk smaller than minChars". The end of a document, and the tail
     * left after a section blows past the tolerance cap, can be a scrap of about twenty characters. Alone, it will
     * not be recalled and it wastes a topK slot. Merging it back is worth it even across a section boundary
     */
    private static void flush(List<ChunkDraft> buffer, List<ChunkDraft> result,
                              ChunkBudget budget, int minChars) {
        if (buffer.isEmpty()) {
            return;
        }
        ChunkDraft packed = buffer.size() == 1 ? buffer.get(0) : merge(buffer);
        if (!result.isEmpty() && contentLength(packed) < minChars) {
            ChunkDraft previous = result.get(result.size() - 1);
            if (contentLength(previous) + SEPARATOR.length() + contentLength(packed) <= budget.toleranceChars()) {
                result.set(result.size() - 1, merge(List.of(previous, packed)));
                return;
            }
        }
        result.add(packed);
    }

    /**
     * Merges several chunks. Display text and retrieval body are concatenated separately, assets are unioned, and
     * the outline path is the common prefix of the chunks
     * <p>
     * The retrieval body is concatenated per chunk as "explicit value first, otherwise fall back to display text".
     * An image chunk's retrieval body deliberately drops URL noise; always taking display text would degrade the
     * vector into text that contains the URL. The path is the common prefix, not any one chunk's path: the lead-in
     * may come from the previous section, and taking the large chunk's path would file the previous section's content under this section's name
     */
    private static ChunkDraft merge(List<ChunkDraft> parts) {
        StringBuilder content = new StringBuilder();
        StringBuilder body = new StringBuilder();
        boolean hasExplicitBody = false;
        boolean heading = false;
        List<AssetRef> assets = new ArrayList<>();

        for (ChunkDraft draft : parts) {
            appendPart(content, draft.content());
            appendPart(body, draft.effectiveBody());
            hasExplicitBody |= draft.hasExplicitBody();
            heading |= draft.heading();
            assets.addAll(draft.metadata().assets());
        }

        ChunkMetadata merged = ChunkMetadata.builder()
                .outlinePath(parts.get(0).metadata().outlinePath().subList(0, commonPrefixLength(parts)))
                .assets(assets)
                .provenance(parts.get(0).metadata().provenance())
                .build();
        return new ChunkDraft(content.toString(), hasExplicitBody ? body.toString() : null,
                merged, false, heading);
    }

    private static int commonPrefixLength(List<ChunkDraft> drafts) {
        List<String> first = drafts.get(0).metadata().outlinePath();
        int common = first.size();
        for (ChunkDraft draft : drafts) {
            common = Math.min(common, commonPrefixLength(first, draft.metadata().outlinePath()));
        }
        return common;
    }

    private static int commonPrefixLength(List<String> a, List<String> b) {
        int limit = Math.min(a.size(), b.size());
        int i = 0;
        while (i < limit && Objects.equals(a.get(i), b.get(i))) {
            i++;
        }
        return i;
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (!StringUtils.hasText(part)) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(SEPARATOR);
        }
        sb.append(part);
    }

    /**
     * Length of several drafts joined together, including the separators between them
     */
    private static int totalLength(List<ChunkDraft> drafts) {
        int len = 0;
        for (int i = 0; i < drafts.size(); i++) {
            len += (i == 0 ? 0 : SEPARATOR.length()) + contentLength(drafts.get(i));
        }
        return len;
    }

    private static int contentLength(ChunkDraft draft) {
        return StringUtils.hasText(draft.content()) ? draft.content().length() : 0;
    }
}
