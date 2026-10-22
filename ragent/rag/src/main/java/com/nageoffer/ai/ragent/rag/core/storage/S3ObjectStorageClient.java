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

import com.nageoffer.ai.ragent.rag.config.RagStorageProperties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * S3-compatible storage (rustfs / minio) implementation of {@link ObjectStorageClient}
 * <p>
 * Registered when {@code rag.storage.type=s3} (the default). Holds the raw S3 logic formerly in S3FileStorageService,
 * without namespace/key assembly or DTO assembly
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.storage.type", havingValue = "s3", matchIfMissing = true)
public class S3ObjectStorageClient implements ObjectStorageClient {

    private static final Duration PRESIGN_DURATION = Duration.ofMinutes(10);
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    /**
     * Object-store base URL a browser can open directly. Defaults to the internal endpoint
     */
    private final String publicBaseUrl;

    public S3ObjectStorageClient(S3Client s3Client, S3Presigner s3Presigner, RagStorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.publicBaseUrl = properties.getS3().resolvePublicUrl();
    }

    /**
     * Build a presigned URL with S3Presigner and stream the upload through HttpURLConnection
     * <p>
     * Why not the SDK putObject / S3TransferManager?
     * Every synchronous and asynchronous upload API in AWS SDK v2 (through 2.40.x) buffers the payload
     * on the heap inside the SigV4 signing pipeline (even RequestBody.fromFile)
     * <p>
     * A presigned URL encodes auth in the query string. The payload uses UNSIGNED-PAYLOAD
     * and does not need to be read ahead to compute SHA-256. HttpURLConnection.setFixedLengthStreamingMode
     * sends the body as a stream, and heap use stays at the internal buffer size
     */
    @Override
    @SneakyThrows
    public void streamPut(String bucket, String key, InputStream content, long size, String contentType) {
        // 1. Build the presigned URL (CPU only, no IO)
        PresignedPutObjectRequest presignedReq = s3Presigner.presignPutObject(p -> p
                .signatureDuration(PRESIGN_DURATION)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build()));

        // 2. Stream the upload
        streamPutViaPresignedUrl(presignedReq, content, size, contentType);
    }

    @Override
    public void reliablePut(String bucket, String key, InputStream content, long size, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromInputStream(content, size));
    }

    @Override
    public InputStream getObject(String bucket, String key) {
        return s3Client.getObject(b -> b.bucket(bucket).key(key));
    }

    @Override
    public void deleteObject(String bucket, String key) {
        s3Client.deleteObject(b -> b.bucket(bucket).key(key));
    }

    @Override
    public void deleteByPrefix(String bucket, String prefix) {
        if (!bucketExists(bucket)) {
            // Idempotent: a missing bucket means the prefix is already empty
            return;
        }

        // Page through every object under the prefix and delete in batches
        String continuationToken = null;
        int cleared = 0;
        do {
            ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build();
            ListObjectsV2Response listResp = s3Client.listObjectsV2(listReq);

            List<ObjectIdentifier> toDelete = listResp.contents().stream()
                    .map(S3Object::key)
                    .map(k -> ObjectIdentifier.builder().key(k).build())
                    .toList();
            if (!toDelete.isEmpty()) {
                s3Client.deleteObjects(b -> b.bucket(bucket).delete(Delete.builder().objects(toDelete).build()));
                cleared += toDelete.size();
            }

            continuationToken = Boolean.TRUE.equals(listResp.isTruncated()) ? listResp.nextContinuationToken() : null;
        } while (continuationToken != null);

        log.info("已清空前缀 bucket={}, prefix={}, 对象数={}", bucket, prefix, cleared);
    }

    @Override
    public boolean objectExists(String bucket, String key) {
        try {
            s3Client.headObject(b -> b.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public boolean bucketExists(String bucket) {
        try {
            s3Client.headBucket(b -> b.bucket(bucket));
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            // RustFS/S3 may return 404 rather than NoSuchBucketException for a missing bucket
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void createBucket(String bucket) {
        try {
            s3Client.createBucket(b -> b.bucket(bucket));
        } catch (BucketAlreadyOwnedByYouException e) {
            // Idempotent: already owned counts as success
        }
    }

    @Override
    public void setBucketPublicRead(String bucket) {
        // Anonymous read policy: allow any principal GetObject, so objects in the bucket can be previewed directly in a browser
        String policy = """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow",\
                "Principal":{"AWS":["*"]},"Action":["s3:GetObject"],\
                "Resource":["arn:aws:s3:::%s/*"]}]}""".formatted(bucket);
        s3Client.putBucketPolicy(b -> b.bucket(bucket).policy(policy));
    }

    @Override
    public String buildPublicUrl(String bucket, String key) {
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/" + bucket + "/" + key;
    }

    /**
     * HTTP PUT streaming upload through a presigned URL
     */
    private void streamPutViaPresignedUrl(PresignedPutObjectRequest presignedReq,
                                          InputStream inputStream,
                                          long size,
                                          String contentType) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) presignedReq.url().openConnection();
        try {
            conn.setDoOutput(true);
            conn.setRequestMethod("PUT");
            conn.setFixedLengthStreamingMode(size);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            presignedReq.signedHeaders()
                    .forEach((k, vs) -> vs.forEach(v -> conn.addRequestProperty(k, v)));

            if (contentType != null && !contentType.isBlank()) {
                conn.setRequestProperty("Content-Type", contentType);
            }

            try (OutputStream out = conn.getOutputStream()) {
                inputStream.transferTo(out);
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String errorBody = readErrorStream(conn);
                throw new IOException(String.format(
                        "S3 流式上传失败: HTTP %d, url=%s, body=%s",
                        code, presignedReq.url(), errorBody));
            }
        } finally {
            conn.disconnect();
        }
    }

    private String readErrorStream(HttpURLConnection conn) {
        try (InputStream err = conn.getErrorStream()) {
            return err != null ? new String(err.readAllBytes(), StandardCharsets.UTF_8) : "(empty)";
        } catch (IOException e) {
            return "(read error: " + e.getMessage() + ")";
        }
    }
}
