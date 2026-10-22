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

package com.nageoffer.ai.ragent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Object storage configuration
 * <p>
 * Same shape as {@code rag.vector.type} (pg/milvus) and {@code rag.keyword.type} (none/es). {@code type}
 * switches between S3-compatible storage (rustfs / minio) and Alibaba Cloud OSS. Every knowledge-base document shares one global bucket {@code kbBucket},
 * and each knowledge base is a directory in that bucket (key prefix = collectionName). Multimodal assets go to the public-read bucket {@code assetBucket}
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.storage")
public class RagStorageProperties {

    /**
     * Storage backend
     * s3 (rustfs / minio, the default) or oss (Alibaba Cloud)
     */
    private String type = "s3";

    /**
     * Global knowledge-base bucket: every knowledge-base document shares it, isolated by collectionName directory
     * <p>
     * Private. Documents are read only by the server via openStream and are not exposed directly. OSS bucket names are globally unique and must be overridden per deployment in production
     */
    private String kbBucket = "ragent-sources";

    /**
     * Multimodal asset bucket: images extracted from PDFs and similar objects. Public read, so the browser can preview them anonymously
     */
    private String assetBucket = "ragent-assets";

    /**
     * S3-compatible storage (used when type=s3)
     */
    private S3 s3 = new S3();

    /**
     * Alibaba Cloud OSS (used when type=oss)
     */
    private Oss oss = new Oss();

    @Data
    public static class S3 {

        /**
         * S3 endpoint, for example rustfs at <a href="http://localhost:9000">...</a>
         */
        private String endpoint;

        /**
         * Access key ID
         */
        private String accessKey;

        /**
         * Access key secret
         */
        private String secretKey;

        /**
         * Region. S3-compatible storage usually accepts any value; default us-east-1
         */
        private String region = "us-east-1";

        /**
         * Force path-style addressing: rustfs / minio require true
         */
        private boolean pathStyle = true;

        /**
         * Public base URL the browser can reach directly, when internal and external endpoints differ. Blank falls back to endpoint
         */
        private String publicUrl;

        /**
         * Public base URL. Blank falls back to endpoint
         */
        public String resolvePublicUrl() {
            return StringUtils.hasText(publicUrl) ? publicUrl : endpoint;
        }
    }

    @Data
    public static class Oss {

        /**
         * OSS endpoint, for example <a href="https://oss-cn-hangzhou.aliyuncs.com">...</a>
         */
        private String endpoint;

        /**
         * Access key ID
         */
        private String accessKey;

        /**
         * Access key secret
         */
        private String secretKey;

        /**
         * Region, for example cn-hangzhou. OSS V4 signing may require it
         */
        private String region;

        /**
         * Public asset base URL (virtual-hosted style, bucket subdomain included), for example <a href="https://ragent-assets.oss-cn-hangzhou.aliyuncs.com">...</a>
         */
        private String publicUrl;
    }
}
