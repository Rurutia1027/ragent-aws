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

package com.nageoffer.ai.ragent.rag.core.graph;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Codec for the graph document source id {collectionName}_{docId}.
 * <p>
 * docId is a numeric snowflake, so anchoring on the trailing "_digits" uniquely recovers both parts.
 * Collection names are user-defined, may contain underscores, and may be prefixes of each other
 * (kb and kb_hr may legally coexist), so a left-to-right substring match mixes collections: the read
 * path would pull another collection's evidence into the primary path, and the delete path would wipe
 * that data irreversibly. Ownership must go through this parse plus full-name equality.
 */
public record GraphFileSource(String collectionName, String docId) {

    /**
     * Right-anchored format: the body is strictly {collectionName}_{docId}, tolerating an extension suffix added by the server.
     */
    private static final Pattern FORMAT = Pattern.compile("^(.+)_(\\d+)((?:\\.[A-Za-z0-9]+)*)$");

    /**
     * Write-side encoding, the inverse of parse.
     */
    public static String encode(String collectionName, String docId) {
        return collectionName + "_" + docId;
    }

    /**
     * Recover the source from a LightRAG file_path. Take the basename first to skip a possible directory prefix; return null when the encoding does not match.
     */
    public static GraphFileSource parse(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            return null;
        }
        String baseName = filePath.substring(filePath.lastIndexOf('/') + 1);
        Matcher matcher = FORMAT.matcher(baseName);
        return matcher.matches() ? new GraphFileSource(matcher.group(1), matcher.group(2)) : null;
    }
}
