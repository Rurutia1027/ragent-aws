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

package com.nageoffer.ai.ragent.rag.core.prompt;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * Prompt build context. Holds every input used to assemble the prompt for one RAG request.
 */
@Data
@Builder
public class PromptContext {

    /**
     * Context text returned by knowledge-base retrieval (already formatted).
     */
    private String kbContext;

    /**
     * Intents hit by the knowledge-base channel, with their scores.
     */
    private List<NodeScore> kbIntents;

    /**
     * Intent ids allowed to take part in template selection and rule injection.
     */
    @Builder.Default
    private Set<String> eligibleIntentIds = Set.of();

    /**
     * Whether knowledge-base context is present.
     */
    public boolean hasKb() {
        return StrUtil.isNotBlank(kbContext);
    }
}
