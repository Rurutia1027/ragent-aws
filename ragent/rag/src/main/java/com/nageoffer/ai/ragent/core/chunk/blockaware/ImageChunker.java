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
import com.nageoffer.ai.ragent.core.parser.model.AssetRef;
import com.nageoffer.ai.ragent.core.parser.model.ImageBlock;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Image chunker: one image, one chunk. Display text is the description plus a markdown image link; embedding text is the description only
 * <p>
 * An image URL in the vector is pure noise, and the link itself is the fallback only when there is no description
 * (for example an image MinerU extracted). Declared flowable so the image shares a chunk with its lead-in or
 * caption, and a retrieval hit brings the image along
 */
@Component
public class ImageChunker implements BlockChunker<ImageBlock> {

    @Override
    public Class<ImageBlock> blockType() {
        return ImageBlock.class;
    }

    @Override
    public List<ChunkDraft> chunk(ImageBlock block, ChunkContext ctx) {
        if (block == null || block.asset() == null) {
            return List.of();
        }
        AssetRef asset = block.asset();
        String markdown = "![" + pickCaption(block) + "](" + asset.publicUrl() + ")";

        String description = block.description();
        boolean hasDescription = description != null && !description.isBlank();
        String content = hasDescription ? description.strip() + "\n\n" + markdown : markdown;

        ChunkMetadata metadata = ChunkMetadata.builder()
                .outlinePath(ctx.outlinePath())
                .assets(List.of(asset))
                .provenance(block.provenance())
                .build();

        return List.of(ChunkDraft.of(content, hasDescription ? description.strip() : null, metadata));
    }

    private String pickCaption(ImageBlock block) {
        if (block.caption() != null && !block.caption().isEmpty()) {
            return block.caption();
        }
        if (block.altText() != null && !block.altText().isEmpty()) {
            return block.altText();
        }
        return "";
    }

}
