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

package com.nageoffer.ai.ragent.knowledge.support;

import com.nageoffer.ai.ragent.core.chunk.model.ChunkBudget;
import com.nageoffer.ai.ragent.core.parser.registry.ParseProfile;
import com.nageoffer.ai.ragent.core.parser.registry.ParserRegistry;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.knowledge.controller.vo.IngestionSpecSchemaVO;
import com.nageoffer.ai.ragent.rag.util.DisplayType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Issues the form schema for the document-level ingestion config: the single point
 * <p>
 * Lives in the same package as {@link IngestionSpecCodec}. They are the two sides of one spec: one reads, writes,
 * and validates, the other says what it looks like and what each field should be filled with. Wire key names and
 * the sentinel therefore always come from the codec; this class does not keep a second copy. Only the parse
 * profile and chunk budget the user can actually control are exposed, ranges included, so the UI does not keep its own validation rules
 */
@Component
@RequiredArgsConstructor
public class IngestionSpecSchemaProvider {

    /**
     * Field name of the profile options in the UI: name the fact the operator can see
     * <p>
     * "Fast / fidelity" names the engine's cost-for-fidelity axis. An operator has one file and cannot answer "how
     * much fidelity do I need", only "does this table have merged cells", so ask what structure the table has
     */
    private static final String PARSE_PROFILE_LABEL = "表格结构";

    private final ParserRegistry parserRegistry;

    /**
     * Startup self-check: the profile copy covers tables only
     * <p>
     * The two sentences below talk about merged cells and multi-row headers, and {@link ParserRegistry#profileSensitiveMimeTypes()}
     * returns only Excel today. The day a non-table format claims a non-fallback profile, this copy would discuss
     * headers on a PDF, so startup fails. Then either change the copy, or move it onto the parser that claims that profile and issue it per format
     */
    @PostConstruct
    void checkProfileCopyCoversAllSensitiveFormats() {
        List<String> unexpected = parserRegistry.profileSensitiveMimeTypes().stream()
                .filter(mime -> !DisplayType.of(null, mime).isTabular())
                .toList();
        if (!unexpected.isEmpty()) {
            throw new ServiceException("Tier copy covers tables only. These MIME types claim a non-fallback tier but are not tables, so the copy needs to change: "
                    + String.join(", ", unexpected));
        }
    }

    /**
     * Builds the form schema
     * <p>
     * The authority for the ranges is {@link ChunkBudget} construction; this only tells the UI the same numbers. The
     * format list a profile applies to is derived from the registry and hardcoded on neither side. A format whose two
     * profiles hit the same parser in the {@code (MIME x profile)} table treats the profile as a no-op, and the option
     * must be hidden. Field names and option names are issued together. The three pieces of copy have to agree with
     * each other ("table structure" with "regular / complex table"). Storing half on each side means one edit is missed
     */
    public IngestionSpecSchemaVO describe() {
        ChunkBudget defaults = ChunkBudget.defaults();
        List<IngestionSpecSchemaVO.Option> profiles = List.of(
                new IngestionSpecSchemaVO.Option(ParseProfile.FAST.getCode(), "规整表格",
                        "一行一条记录、表头只有一层，秒级完成"),
                new IngestionSpecSchemaVO.Option(ParseProfile.FIDELITY.getCode(), "复杂表格",
                        "有合并单元格、多层表头或跨页表格；需要数十秒，走外部解析服务"));
        List<String> profileExtensions = parserRegistry.profileSensitiveMimeTypes().stream()
                .map(mime -> DisplayType.of(null, mime))
                .filter(type -> type != DisplayType.OTHER)
                .flatMap(type -> type.extensions().stream())
                .distinct()
                .sorted()
                .toList();
        // hint answers "what this number is"; detail answers "what happens if you turn it up or down". Neither restates the field name
        List<IngestionSpecSchemaVO.BudgetField> fields = List.of(
                new IngestionSpecSchemaVO.BudgetField(IngestionSpecCodec.KEY_MAX_CHARS, "块大小", defaults.maxChars(),
                        1, ChunkBudget.MAX_CHARS_LIMIT, 512, ChunkBudget.MAX_CHARS_LIMIT,
                        "一段目标放多少字",
                        "这是目标，不是硬上限。为保住整章或整张表不被切开，一段最多能撑到「块大小 × 结构容忍倍数」。"
                                + "调小则检索更精准，但每段带的上下文更少"),
                new IngestionSpecSchemaVO.BudgetField(IngestionSpecCodec.KEY_OVERLAP_CHARS, "块重叠", defaults.overlapChars(),
                        0, ChunkBudget.MAX_CHARS_LIMIT - 1, 64, 1024,
                        "相邻两段重复多少字",
                        "只在单个段落超过块大小、必须拦腰切开时才生效，多数段落到不了这一步。它同时是切口回退找"
                                + "句号的最大距离，填太小会切在句子中间。默认取块大小的 1/" + ChunkBudget.OVERLAP_DIVISOR
                                + "，且必须小于块大小"),
                new IngestionSpecSchemaVO.BudgetField(IngestionSpecCodec.KEY_ROWS_PER_CHUNK, "表格每块行数", defaults.rowsPerChunk(),
                        1, ChunkBudget.ROWS_PER_CHUNK_LIMIT, 20, 50,
                        "表格每段放多少数据行",
                        "列多、每格字数长的表要调小"),
                new IngestionSpecSchemaVO.BudgetField(IngestionSpecCodec.KEY_TOLERANCE_FACTOR, "结构容忍倍数",
                        defaults.toleranceFactor(), 1, ChunkBudget.TOLERANCE_FACTOR_LIMIT, 2, 4,
                        "一段最多可超出块大小的倍数",
                        "为保住整章、整张表、整个代码块不被切开，允许一段撑到块大小的几倍。填 1 就是严格按块大小切，"
                                + "章节会被切碎；调大则块更完整，但检索更粗、单次问答塞给模型的上下文也更多"));
        return new IngestionSpecSchemaVO(
                PARSE_PROFILE_LABEL, profiles, profileExtensions, fields, IngestionSpecCodec.WHOLE_DOCUMENT_SENTINEL);
    }
}
