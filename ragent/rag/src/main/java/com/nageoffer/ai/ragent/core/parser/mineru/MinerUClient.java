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

package com.nageoffer.ai.ragent.core.parser.mineru;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * MinerU SaaS HTTP client
 * <p>
 * Four core methods, on the "local file batch upload and parse" path:
 * <ul>
 *   <li>{@link #requestUpload} applies for an upload URL and returns batch_id + upload URL</li>
 *   <li>{@link #uploadFile} PUTs the file bytes to MinerU OSS</li>
 *   <li>{@link #queryResult} queries task status (for polling)</li>
 *   <li>{@link #downloadZip} downloads the result zip bytes</li>
 * </ul>
 * <p>
 * Auth: HTTP header {@code Authorization: Bearer <api-key>} (upload and download use a presigned URL and need no auth header).
 * Response parsing uses {@link JsonNode} so small MinerU field changes stay compatible; only the fields we care about are read
 */
@Slf4j
@Component
public class MinerUClient {

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final MinerUProperties properties;

    public MinerUClient(@Qualifier("syncHttpClient") OkHttpClient httpClient,
                        ObjectMapper objectMapper,
                        MinerUProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Applies for an upload URL (step one of local file batch upload and parse)
     * <p>
     * Submits file metadata only (no url). MinerU returns a batch_id and a presigned upload URL.
     * After that, {@link #uploadFile} must PUT the file bytes; only then does MinerU submit the parse automatically
     *
     * @param request single-file parse request
     * @return credential containing batch_id + upload URL
     * @throws ServiceException network error / missing api-key / SaaS error / missing upload URL
     */
    public BatchUploadTicket requestUpload(BatchSubmitRequest request) {
        requireApiKey();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("enable_formula", request.enableFormula());
        body.put("enable_table", request.enableTable());
        body.put("language", request.language() == null ? "ch" : request.language());

        ArrayNode files = body.putArray("files");
        ObjectNode file = files.addObject();
        if (request.fileName() != null) {
            file.put("name", request.fileName());
        }
        file.put("is_ocr", request.isOcr());
        if (request.dataId() != null) {
            file.put("data_id", request.dataId());
        }

        String url = properties.getApiUrl() + "/file-urls/batch";
        Request httpRequest = newJsonPost(url, body.toString());

        JsonNode root = executeAndParse(httpRequest, "requestUpload");
        ensureSuccess(root, "requestUpload");

        JsonNode data = root.path("data");
        String batchId = data.path("batch_id").asText(null);
        if (batchId == null || batchId.isBlank()) {
            throw new ServiceException("MinerU requestUpload response is missing batch_id, body=" + root);
        }

        JsonNode fileUrls = data.path("file_urls");
        if (!fileUrls.isArray() || fileUrls.isEmpty()) {
            throw new ServiceException("MinerU requestUpload response is missing file_urls, body=" + root);
        }
        String uploadUrl = fileUrls.get(0).asText(null);
        if (uploadUrl == null || uploadUrl.isBlank()) {
            throw new ServiceException("MinerU requestUpload returned an empty file_urls[0], body=" + root);
        }

        log.info("MinerU 申请上传链接成功 batchId={} fileName={}", batchId, request.fileName());
        return new BatchUploadTicket(batchId, uploadUrl);
    }

    /**
     * Uploads file bytes to the MinerU presigned URL (step two of local file batch upload and parse)
     * <p>
     * Note: the target is an OSS presigned PUT link. MinerU requires <b>no Content-Type and no Authorization</b>.
     * After a successful upload MinerU detects the file and submits the parse, and {@link #queryResult} can poll
     *
     * @param uploadUrl upload URL returned by {@link #requestUpload}
     * @param content   raw file bytes
     * @throws ServiceException network error / non-2xx response
     */
    public void uploadFile(String uploadUrl, byte[] content) {
        if (uploadUrl == null || uploadUrl.isBlank()) {
            throw new ServiceException("uploadUrl cannot be empty");
        }
        if (content == null || content.length == 0) {
            throw new ServiceException("Upload bytes cannot be empty");
        }
        // Presigned PUT: the body has no Content-Type (pass null) and no Authorization
        Request httpRequest = new Request.Builder()
                .url(uploadUrl)
                .put(RequestBody.create(content, null))
                .build();
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String body = readBodySafe(response);
                throw new ServiceException("MinerU uploadFile failed code=" + response.code() + " body=" + body);
            }
            log.info("MinerU 文件上传成功 size={} url={}", content.length, uploadUrl);
        } catch (IOException e) {
            throw new ServiceException("MinerU uploadFile network error: " + e.getMessage());
        }
    }

    /**
     * Queries task status
     */
    public MinerUStatus queryResult(String batchId) {
        requireApiKey();
        if (batchId == null || batchId.isBlank()) {
            throw new ServiceException("batchId cannot be empty");
        }

        // MinerU v4 queries results by batch_id: batch_id is a path segment (/extract-results/batch/{batch_id}), not a query parameter
        String url = properties.getApiUrl() + "/extract-results/batch/" + batchId;
        Request httpRequest = newGet(url);

        JsonNode root = executeAndParse(httpRequest, "queryResult");
        ensureSuccess(root, "queryResult");

        JsonNode data = root.path("data");
        JsonNode extractResult = data.path("extract_result");
        if (!extractResult.isArray() || extractResult.isEmpty()) {
            // The task may still be queued, so extract_result is absent for now
            return new MinerUStatus(MinerUTaskState.RUNNING, null, null);
        }

        // A single-file submission; take only the first result
        JsonNode item = extractResult.get(0);
        String stateRaw = item.path("state").asText(null);
        MinerUTaskState state = MinerUTaskState.parse(stateRaw);
        String zipUrl = item.path("full_zip_url").asText(null);
        String errMsg = item.path("err_msg").asText(null);

        return new MinerUStatus(state, zipUrl, errMsg);
    }

    /**
     * Downloads the result zip bytes
     * <p>
     * Note: this URL is usually a one-time presigned URL and it expires. Download as soon as MinerUStatus.zipUrl is available
     */
    public byte[] downloadZip(String zipUrl) {
        if (zipUrl == null || zipUrl.isBlank()) {
            throw new ServiceException("zipUrl cannot be empty");
        }
        Request httpRequest = new Request.Builder().url(zipUrl).get().build();
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String body = readBodySafe(response);
                throw new ServiceException("MinerU downloadZip failed code=" + response.code() + " body=" + body);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new ServiceException("MinerU downloadZip response body is empty");
            }
            return body.bytes();
        } catch (IOException e) {
            throw new ServiceException("MinerU downloadZip network error: " + e.getMessage());
        }
    }

    // ============== private helpers ==============

    private void requireApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new ServiceException("MinerU api-key is not configured. Set the MINERU_API_KEY environment variable");
        }
    }

    private Request newJsonPost(String url, String jsonBody) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON_MEDIA))
                .build();
    }

    private Request newGet(String url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .get()
                .build();
    }

    private JsonNode executeAndParse(Request request, String opName) {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = readBodySafe(response);
            if (!response.isSuccessful()) {
                throw new ServiceException(String.format(
                        "MinerU %s HTTP error code=%d, body=%s", opName, response.code(), body));
            }
            try {
                return objectMapper.readTree(body);
            } catch (IOException e) {
                throw new ServiceException("MinerU " + opName + " response is not JSON: " + body);
            }
        } catch (IOException e) {
            throw new ServiceException("MinerU " + opName + " network error: " + e.getMessage());
        }
    }

    /**
     * Checks the MinerU business code and throws when it is not 0
     * <p>
     * MinerU's standard response is {@code {"code":0,"msg":"ok","data":{...}}}
     */
    private void ensureSuccess(JsonNode root, String opName) {
        int code = root.path("code").asInt(-1);
        if (code != 0) {
            String msg = root.path("msg").asText("unknown");
            throw new ServiceException(String.format(
                    "MinerU %s business error code=%d msg=%s", opName, code, msg));
        }
    }

    private String readBodySafe(Response response) {
        try {
            ResponseBody body = response.body();
            return body == null ? "" : body.string();
        } catch (IOException e) {
            return "";
        }
    }
}
