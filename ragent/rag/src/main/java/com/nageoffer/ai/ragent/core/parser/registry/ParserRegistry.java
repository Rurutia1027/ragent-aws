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

package com.nageoffer.ai.ragent.core.parser.registry;

import com.nageoffer.ai.ragent.core.parser.DocumentParser;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.mime.MimeTypes;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parser registry: (MIME x parse profile) -> parser. The table is built at startup, and startup fails if two parsers claim the same key
 * <p>
 * Four lookup steps. If none hit, fail explicitly; do not silently fall back to some general parser:
 * <pre>
 *   exact + requested profile -> wildcard + requested profile -> exact + FAST -> wildcard + FAST -> error
 * </pre>
 * FAST is the global fallback profile. A parser declares a profile explicitly only where it has its own implementation
 */
@Slf4j
@Component
public class ParserRegistry {

    private static final String WILDCARD_SUFFIX = "/*";

    /**
     * Startup self-check list: extensions declared as supported. Each must be claimed exactly by some parser; one miss fails startup
     * <p>
     * The list is extensions, not MIME types, because MIME is the detector's own output and checking the detector
     * against itself is always true. Failing to claim an unheard-of MIME (Tika detects {@code .md} as
     * {@code text/x-web-markdown}) only falls silently through to the wildcard fallback
     */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx",
            "xls", "xlsx", "csv",
            "md", "markdown", "txt", "text",
            "html", "htm", "json", "xml", "rtf",
            "png", "jpg", "jpeg", "svg"
    );

    /**
     * Exact table: profile -> (full MIME -> parser)
     */
    private final Map<ParseProfile, Map<String, DocumentParser>> exact = new EnumMap<>(ParseProfile.class);

    /**
     * Wildcard table: profile -> (MIME major-type prefix -> parser), for example "text" -> Tika
     */
    private final Map<ParseProfile, Map<String, DocumentParser>> wildcard = new EnumMap<>(ParseProfile.class);

    public ParserRegistry(List<DocumentParser> parsers) {
        for (DocumentParser parser : parsers) {
            Map<ParseProfile, Set<String>> claims = parser.supportedMimeTypes();
            if (claims == null || claims.isEmpty()) {
                throw new ServiceException("Parser does not declare any (MIME x tier) claim: " + parser.getParserType());
            }
            claims.forEach((profile, mimeTypes) -> register(parser, profile, mimeTypes));
        }
    }

    private void register(DocumentParser parser, ParseProfile profile, Set<String> mimeTypes) {
        if (profile == null || mimeTypes == null || mimeTypes.isEmpty()) {
            throw new ServiceException("Parser claim list contains an empty tier or an empty MIME set: " + parser.getParserType());
        }
        for (String raw : mimeTypes) {
            if (!StringUtils.hasText(raw)) {
                throw new ServiceException("Parser claimed an empty MIME: " + parser.getParserType());
            }
            String mime = raw.trim().toLowerCase(Locale.ROOT);
            boolean isWildcard = mime.endsWith(WILDCARD_SUFFIX);
            String key = isWildcard ? mime.substring(0, mime.length() - WILDCARD_SUFFIX.length()) : mime;
            Map<String, DocumentParser> table = (isWildcard ? wildcard : exact)
                    .computeIfAbsent(profile, k -> new HashMap<>());
            DocumentParser previous = table.put(key, parser);
            if (previous != null && previous != parser) {
                throw new ServiceException(String.format(
                        "Parser route key conflict: tier=%s MIME=%s is claimed by both %s and %s. Distinguish them explicitly",
                        profile.getCode(), raw, previous.getParserType(), parser.getParserType()));
            }
        }
    }

    @PostConstruct
    public void selfCheck() {
        MimeTypes mimeTypes = MimeTypes.getDefaultMimeTypes();
        Tika tika = new Tika(mimeTypes);
        Map<String, DocumentParser> exactFallback = exact.getOrDefault(ParseProfile.FAST, Map.of());
        List<String> unclaimed = new ArrayList<>();
        for (String extension : new TreeSet<>(SUPPORTED_EXTENSIONS)) {
            String mime = tika.detect("probe." + extension);
            if (!exactFallback.containsKey(mime)) {
                unclaimed.add("." + extension + " → " + mime);
            }
        }
        if (!unclaimed.isEmpty()) {
            throw new ServiceException("Parser registry self-check failed. The MIME detected for these extensions has no exact claim, "
                    + "these would only hit the wildcard fallback: " + String.join(", ", unclaimed));
        }
        // A non-fallback profile accepts only exact MIME. A wildcard claim cannot be enumerated into concrete formats, so the profile difference would be invisible outside
        List<String> illegalWildcards = new ArrayList<>();
        wildcard.forEach((profile, table) -> {
            if (profile != ParseProfile.FAST) {
                table.keySet().forEach(prefix -> illegalWildcards.add(profile.getCode() + " → " + prefix + WILDCARD_SUFFIX));
            }
        });
        if (!illegalWildcards.isEmpty()) {
            throw new ServiceException("Non-fallback tiers allow only exact MIME claims. These wildcard claims cannot be enumerated into concrete formats: "
                    + String.join(", ", illegalWildcards));
        }
        log.info("解析器注册表就绪 精确键={} 通配键={} 自检扩展名={} 全部精确命中",
                countKeys(exact), countKeys(wildcard), SUPPORTED_EXTENSIONS.size());
    }

    /**
     * Looks up a parser by (MIME x profile)
     *
     * @param mimeType real MIME; a {@code ;charset=} parameter is allowed
     * @param profile  requested profile; empty means the default profile
     */
    public Optional<DocumentParser> find(String mimeType, ParseProfile profile) {
        String mime = normalize(mimeType);
        if (mime == null) {
            return Optional.empty();
        }
        ParseProfile requested = profile == null ? ParseProfile.defaultProfile() : profile;
        DocumentParser hit = lookup(mime, requested);
        if (hit == null && requested != ParseProfile.FAST) {
            hit = lookup(mime, ParseProfile.FAST);
        }
        return Optional.ofNullable(hit);
    }

    /**
     * Looks up a parser by (MIME x profile). If it cannot be recognized, throw, rather than handing it to a general parser that emits garbage text
     */
    public DocumentParser require(String mimeType, ParseProfile profile) {
        return find(mimeType, profile).orElseThrow(() -> new ClientException(
                "MIME not found [" + mimeType + "] 在档位 ["
                        + (profile == null ? ParseProfile.defaultProfile() : profile).getCode() + "] 下对应的解析器"));
    }

    /**
     * MIME types for which the profile actually differs: the parser hit by some non-fallback profile is not the parser hit by the fallback profile
     * <p>
     * Lets the layer above decide whether the "parse profile" option should be shown to the user. When both profiles
     * hit the same parser, the profile is a no-op, so this is computed rather than a hardcoded format list
     */
    public Set<String> profileSensitiveMimeTypes() {
        Set<String> sensitive = new TreeSet<>();
        for (ParseProfile profile : ParseProfile.values()) {
            if (profile == ParseProfile.FAST) {
                continue;
            }
            exact.getOrDefault(profile, Map.of()).forEach((mime, parser) -> {
                if (lookup(mime, ParseProfile.FAST) != parser) {
                    sensitive.add(mime);
                }
            });
        }
        return sensitive;
    }

    /**
     * Whether any profile can parse this MIME. Used to reject an upload up front
     */
    public boolean canParse(String mimeType) {
        String mime = normalize(mimeType);
        if (mime == null) {
            return false;
        }
        for (ParseProfile profile : ParseProfile.values()) {
            if (lookup(mime, profile) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Single-profile lookup: an exact key beats a wildcard key
     */
    private DocumentParser lookup(String mime, ParseProfile profile) {
        DocumentParser hit = exact.getOrDefault(profile, Map.of()).get(mime);
        if (hit != null) {
            return hit;
        }
        int slash = mime.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        return wildcard.getOrDefault(profile, Map.of()).get(mime.substring(0, slash));
    }

    private static String normalize(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return null;
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static int countKeys(Map<ParseProfile, Map<String, DocumentParser>> table) {
        return table.values().stream().mapToInt(Map::size).sum();
    }
}
