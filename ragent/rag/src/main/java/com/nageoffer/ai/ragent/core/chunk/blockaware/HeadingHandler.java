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

import com.nageoffer.ai.ragent.core.parser.model.HeadingBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Heading handler: pops the stack by the original heading level and maintains the outline path the dispatcher holds
 * <p>
 * Stateless. Concurrent ingests share one instance; the caller owns the path and passes it in per block
 */
@Component
public class HeadingHandler {

    /**
     * Outline path together with the original heading level of each entry
     * <p>
     * Levels have to be kept. Path depth alone cannot tell which level a new heading hangs under, and a document
     * that does not start at H1 would nest sibling sections inside each other
     */
    public record Outline(List<String> path, List<Integer> levels) {

        public static final Outline EMPTY = new Outline(List.of(), List.of());

        public Outline {
            path = path == null ? List.of() : List.copyOf(path);
            levels = levels == null ? List.of() : List.copyOf(levels);
        }
    }

    /**
     * Updates the outline path from a heading. Both the argument and the return value are immutable
     */
    public Outline update(Outline current, HeadingBlock heading) {
        Outline base = current == null ? Outline.EMPTY : current;
        if (heading == null) {
            return base;
        }
        int level = Math.max(1, heading.level());

        // Pop siblings and deeper ancestors. The real parent is the nearest heading with a smaller level
        int keep = base.levels().size();
        while (keep > 0 && base.levels().get(keep - 1) >= level) {
            keep--;
        }

        List<String> path = new ArrayList<>(base.path().subList(0, keep));
        List<Integer> levels = new ArrayList<>(base.levels().subList(0, keep));
        path.add(heading.text() == null ? "" : heading.text());
        levels.add(level);
        return new Outline(path, levels);
    }
}
