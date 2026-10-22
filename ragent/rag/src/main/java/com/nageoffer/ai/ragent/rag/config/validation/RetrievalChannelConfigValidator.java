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

package com.nageoffer.ai.ragent.rag.config.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Consistency check of retrieval-channel backend assembly versus channel enablement (pure logic, no Spring, easy to unit test)
 * <p>
 * The two layers are fully orthogonal:
 * <ul>
 *   <li>Backend assembly {@code rag.{keyword,graph}.type}: whether {@code @ConditionalOnProperty} registers the implementation bean.
 *       {@code none} (or an illegal value) means the channel class never enters the container</li>
 *   <li>Channel enablement {@code rag.search.channels.*.enabled}: read by {@code isEnabled()} at retrieval time, and only after the channel bean exists</li>
 * </ul>
 * Effective participation = backend assembled AND channel enabled. So {@code type=none but enabled=true} is a dead switch:
 * the user thinks that retrieval path is on, but the channel class was never registered and nobody reads {@code enabled=true}. This validator catches that one-way contradiction
 * <p>
 * The one-way rule is deliberate: the reverse, {@code type=lightrag but enabled=false}, is legal (the backend is visualization / read-only and does not participate in recall) and is not reported
 */
public final class RetrievalChannelConfigValidator {

    private RetrievalChannelConfigValidator() {
    }

    /**
     * One contradiction: the retrieval channel is enabled but its backend is not assembled
     *
     * @param channelLabel channel display name, used in the error
     * @param typeKey      backend type property key, such as rag.keyword.type
     * @param actualType   actual value of that key; may be empty
     * @param requiredType value required to assemble that backend, such as es / lightrag
     * @param enabledKey   channel enablement property key, such as rag.search.channels.keyword.enabled
     * @param enableHint   extra hint, besides setting type, if the user chooses to enable that retrieval
     */
    public record Violation(String channelLabel,
                            String typeKey,
                            String actualType,
                            String requiredType,
                            String enabledKey,
                            String enableHint) {
    }

    /**
     * Channel spec to validate. Adding a retrieval path means adding one entry to {@link #SPECS}
     */
    private record ChannelSpec(String label,
                               String typeKey,
                               String requiredType,
                               String enabledKey,
                               String enableHint) {
    }

    private static final List<ChannelSpec> SPECS = List.of(
            new ChannelSpec("关键词检索", "rag.keyword.type", "es",
                    "rag.search.channels.keyword.enabled", "并配置 rag.keyword.es.*"),
            new ChannelSpec("图谱检索", "rag.graph.type", "lightrag",
                    "rag.search.channels.graph.enabled", "并确保 LightRAG 服务可达（rag.graph.lightrag.base-url）")
    );

    /**
     * Validate every channel and collect all violations at once (do not stop at the first), so the user can fix them in one pass
     *
     * @param typeReader    reads the actual type value (null when absent)
     * @param enabledReader reads the channel enablement switch (false when absent)
     * @return violations; empty means the configuration is consistent
     */
    public static List<Violation> validate(Function<String, String> typeReader,
                                           Predicate<String> enabledReader) {
        List<Violation> violations = new ArrayList<>();
        for (ChannelSpec spec : SPECS) {
            String actualType = typeReader.apply(spec.typeKey());
            // Backend not assembled: type missing, blank, or not the required value (case-insensitive, aligned with @ConditionalOnProperty)
            boolean backendOff = actualType == null
                    || actualType.isBlank()
                    || !actualType.trim().equalsIgnoreCase(spec.requiredType());
            if (backendOff && enabledReader.test(spec.enabledKey())) {
                violations.add(new Violation(spec.label(), spec.typeKey(),
                        actualType == null ? "" : actualType, spec.requiredType(),
                        spec.enabledKey(), spec.enableHint()));
            }
        }
        return violations;
    }
}
