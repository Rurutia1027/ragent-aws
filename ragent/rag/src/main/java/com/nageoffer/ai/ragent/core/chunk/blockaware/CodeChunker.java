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

import com.nageoffer.ai.ragent.core.chunk.model.ChunkDraft;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkMetadata;
import com.nageoffer.ai.ragent.core.parser.model.CodeBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Code-block chunker: keep the block whole, and fall back to splitting on line boundaries only when it exceeds the tolerance cap. Each chunk repeats the fence
 * <p>
 * A half line or a missing fence breaks rendering and understanding, so the default is not to cut. But an indented
 * paragraph in a txt file is parsed as a code block, and a real source file can also far exceed the budget. One chunk
 * that blows through the embedding model's input limit is silently truncated, and the tail is effectively never stored
 */
@Component
public class CodeChunker implements BlockChunker<CodeBlock> {

    @Override
    public Class<CodeBlock> blockType() {
        return CodeBlock.class;
    }

    @Override
    public List<ChunkDraft> chunk(CodeBlock block, ChunkContext ctx) {
        if (block == null) {
            return List.of();
        }
        String language = block.language() == null ? "" : block.language();
        String code = block.code() == null ? "" : block.code();

        ChunkMetadata metadata = ChunkMetadata.builder()
                .outlinePath(ctx.outlinePath())
                .provenance(block.provenance())
                .build();

        List<String> segments = code.length() <= ctx.budget().toleranceChars()
                ? List.of(code)
                : splitByLines(code, ctx.budget().maxChars());

        List<ChunkDraft> result = new ArrayList<>(segments.size());
        for (String segment : segments) {
            String markdown = "```" + language + "\n" + segment + "\n```";
            result.add(ChunkDraft.of(markdown, segment, metadata));
        }
        return ChunkDraft.pieces(result);
    }

    /**
     * Accumulates by line. A single line over budget becomes its own chunk; never cut through the middle of a line
     */
    private static List<String> splitByLines(String code, int maxChars) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : code.split("\n", -1)) {
            int addition = current.isEmpty() ? line.length() : current.length() + 1 + line.length();
            if (!current.isEmpty() && addition > maxChars) {
                segments.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            segments.add(current.toString());
        }
        return segments.isEmpty() ? List.of(code) : segments;
    }
}
