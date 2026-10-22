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

package com.nageoffer.ai.ragent.rag.core.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.ObjectMetadata;
import com.nageoffer.ai.ragent.rag.config.RagStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.List;

/**
 * Alibaba Cloud OSS implementation of {@link ObjectStorageClient}
 * <p>
 * Registered when {@code rag.storage.type=oss}. OSS SDK putObject with contentLength already streams in blocks
 * and does not buffer the whole payload on the heap, and it retries internally, so streamPut and reliablePut share one call and match S3's zero-heap goal.
 * Presigning does not need to be forced onto OSS
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.storage.type", havingValue = "oss")
public class OssObjectStorageClient implements ObjectStorageClient {

    /**
     * OSS deleteObjects limit per call: 1000
     */
    private static final int DELETE_BATCH = 1000;

    private final OSS ossClient;
    private final RagStorageProperties.Oss ossProperties;

    public OssObjectStorageClient(OSS ossClient, RagStorageProperties properties) {
        this.ossClient = ossClient;
        this.ossProperties = properties.getOss();
    }

    @Override
    public void streamPut(String bucket, String key, InputStream content, long size, String contentType) {
        put(bucket, key, content, size, contentType);
    }

    @Override
    public void reliablePut(String bucket, String key, InputStream content, long size, String contentType) {
        put(bucket, key, content, size, contentType);
    }

    private void put(String bucket, String key, InputStream content, long size, String contentType) {
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(size);
        if (StringUtils.hasText(contentType)) {
            meta.setContentType(contentType);
        }
        ossClient.putObject(bucket, key, content, meta);
    }

    @Override
    public InputStream getObject(String bucket, String key) {
        return ossClient.getObject(bucket, key).getObjectContent();
    }

    @Override
    public void deleteObject(String bucket, String key) {
        ossClient.deleteObject(bucket, key);
    }

    @Override
    public void deleteByPrefix(String bucket, String prefix) {
        if (!bucketExists(bucket)) {
            // Idempotent: a missing bucket means the prefix is already empty
            return;
        }

        String nextMarker = null;
        int cleared = 0;
        ObjectListing listing;
        do {
            ListObjectsRequest listReq = new ListObjectsRequest(bucket)
                    .withPrefix(prefix)
                    .withMarker(nextMarker)
                    .withMaxKeys(DELETE_BATCH);
            listing = ossClient.listObjects(listReq);

            List<String> keys = listing.getObjectSummaries().stream()
                    .map(OSSObjectSummary::getKey)
                    .toList();
            if (!keys.isEmpty()) {
                ossClient.deleteObjects(new DeleteObjectsRequest(bucket).withKeys(keys));
                cleared += keys.size();
            }

            nextMarker = listing.getNextMarker();
        } while (listing.isTruncated());

        log.info("已清空前缀 bucket={}, prefix={}, 对象数={}", bucket, prefix, cleared);
    }

    @Override
    public boolean objectExists(String bucket, String key) {
        return ossClient.doesObjectExist(bucket, key);
    }

    @Override
    public boolean bucketExists(String bucket) {
        return ossClient.doesBucketExist(bucket);
    }

    @Override
    public void createBucket(String bucket) {
        if (ossClient.doesBucketExist(bucket)) {
            // Idempotent: already exists counts as success
            return;
        }
        try {
            ossClient.createBucket(bucket);
        } catch (OSSException e) {
            // Created concurrently by someone else: BucketAlreadyExists counts as success
            if (!"BucketAlreadyExists".equals(e.getErrorCode())) {
                throw e;
            }
        }
    }

    @Override
    public void setBucketPublicRead(String bucket) {
        ossClient.setBucketAcl(bucket, CannedAccessControlList.PublicRead);
    }

    @Override
    public String buildPublicUrl(String bucket, String key) {
        String base = StringUtils.hasText(ossProperties.getPublicUrl())
                ? stripTrailingSlash(ossProperties.getPublicUrl())
                : virtualHostedBase(bucket);
        return base + "/" + key;
    }

    /**
     * When publicUrl is not set, derive the virtual-hosted base from the endpoint:
     * {@code https://oss-cn-hangzhou.aliyuncs.com} to {@code https://{bucket}.oss-cn-hangzhou.aliyuncs.com}
     */
    private String virtualHostedBase(String bucket) {
        String endpoint = stripTrailingSlash(ossProperties.getEndpoint());
        int schemeIdx = endpoint.indexOf("://");
        if (schemeIdx < 0) {
            return "https://" + bucket + "." + endpoint;
        }
        String scheme = endpoint.substring(0, schemeIdx);
        String host = endpoint.substring(schemeIdx + 3);
        return scheme + "://" + bucket + "." + host;
    }

    private String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
