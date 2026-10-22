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

package com.nageoffer.ai.ragent.rag.controller.request;

import com.nageoffer.ai.ragent.ingestion.domain.enums.SourceType;
import lombok.Data;

import java.util.Map;

/**
 * Document source request
 * Document source for creating an ingestion task: source type, location, file name, and credentials
 */
@Data
public class DocumentSourceRequest {

    /**
     * Document source type
     * For example file, url, feishu, or s3
     */
    private SourceType type;

    /**
     * Where the document is reached
     * A file path, a URL, or a resource id on a third-party platform
     */
    private String location;

    /**
     * Document file name
     */
    private String fileName;

    /**
     * Credentials required to access the document
     * Key-value pairs such as an API token or a username and password
     */
    private Map<String, String> credentials;
}
