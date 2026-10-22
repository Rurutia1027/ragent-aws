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

package com.nageoffer.ai.ragent.rag.core.source;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.SourceRef;
import com.nageoffer.ai.ragent.rag.config.RAGConfigProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inject request-level citation numbers into an already formatted knowledge-base context
 * <p>
 * Formatting writes only the internal {@code data-ragent-doc-id}. After sources are assembled, that is replaced
 * with the model-visible {@code ref} from {@link SourceRef#getIndex()}. Prompt, SSE, persistence, and the frontend
 * then share one source numbering, and the internal document id is not shown to the model
 * <p>
 * When citations are off, treat it as "no sources": strip the internal anchors and do not inject numbers. Either way,
 * context must pass through this step or the internal docId leaks into model-visible text
 */
@Component
@RequiredArgsConstructor
public class CitationContextEnricher {

    private static final Pattern CONTENT_TAG = Pattern.compile(
            "(?m)^<content([^>]*) data-ragent-doc-id=\"([^\"]*)\">$");

    private final RAGConfigProperties ragConfigProperties;

    public String enrich(String kbContext, List<SourceRef> sources) {
        Map<String, Integer> indexByDocId = Boolean.TRUE.equals(ragConfigProperties.getCitationEnabled())
                ? indexByDocId(sources)
                : Map.of();
        return replaceAnchors(kbContext, indexByDocId);
    }

    /**
     * Strip internal anchors and do not inject numbers, for callers that cannot render citation markers (for example the retrieval tool in agent mode)
     */
    public String stripDocIdAnchors(String kbContext) {
        return replaceAnchors(kbContext, Map.of());
    }

    private String replaceAnchors(String kbContext, Map<String, Integer> indexByDocId) {
        if (StrUtil.isBlank(kbContext)) {
            return StrUtil.emptyIfNull(kbContext);
        }

        Matcher matcher = CONTENT_TAG.matcher(kbContext);
        StringBuilder result = new StringBuilder(kbContext.length());
        while (matcher.find()) {
            String attributes = matcher.group(1);
            String docId = matcher.group(2);
            Integer index = indexByDocId.get(docId);
            String replacement = index == null
                    ? "<content" + attributes + ">"
                    : "<content" + attributes + " ref=\"" + index + "\">";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Map<String, Integer> indexByDocId(List<SourceRef> sources) {
        if (CollUtil.isEmpty(sources)) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (SourceRef source : sources) {
            if (source == null
                    || StrUtil.isBlank(source.getDocId())
                    || source.getIndex() == null) {
                continue;
            }
            result.putIfAbsent(source.getDocId(), source.getIndex());
        }
        return result;
    }
}
