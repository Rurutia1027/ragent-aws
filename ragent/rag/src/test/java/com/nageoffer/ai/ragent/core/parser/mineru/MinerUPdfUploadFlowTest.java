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

import com.nageoffer.ai.ragent.TestRagentApplication;
import com.nageoffer.ai.ragent.core.parser.BlockTextRenderer;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU real-environment integration test: upload a local PDF, parse it with MinerU SaaS, write the zip to disk, and print the markdown
 * <p>
 * <b>Uses no mocks</b>. It runs the real Spring Boot environment and depends on:
 * <ul>
 *   <li>environment variable {@code MINERU_API_KEY} is set</li>
 *   <li>RustFS (image upload) and the other Spring dependencies (DB / Redis, and so on) are reachable</li>
 *   <li>the local PDF is given by {@code -Dmineru.test.pdf=/abs/path/to.pdf}</li>
 * </ul>
 * <p>
 * Example run:
 * <pre>
 * mvn -pl bootstrap test -Dtest=MinerUPdfUploadFlowTest \
 *     -Dmineru.test.pdf=/Users/machen/Downloads/sample.pdf \
 *     -DfailIfNoTests=false
 * </pre>
 * <p>
 * Without {@code mineru.test.pdf} the whole class is skipped (Spring context is not started) and daily builds are unaffected
 * <p>
 * The zip returned by MinerU is unpacked automatically under {@code .mineru-output/} at the repository root (a temp directory, already in .gitignore)
 */
@SpringBootTest(classes = TestRagentApplication.class, webEnvironment = WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "mineru.test.pdf", matches = ".+")
@DisplayName("MinerU PDF 上传解析全流程(真实环境)")
class MinerUPdfUploadFlowTest {

    /**
     * Directory name for MinerU output (under the repository root, dot prefix, gitignored)
     */
    private static final String OUT_DIR_NAME = ".output";

    @Autowired
    private MinerUDocumentParser parser;

    @Autowired
    private MinerUClient minerUClient;

    @Test
    @DisplayName("上传本地 PDF → MinerU 解析 → 落盘并解压 zip + 打印 Markdown")
    void uploadRealPdf_fullFlow() throws Exception {
        Path pdf = Path.of(System.getProperty("mineru.test.pdf"));
        byte[] pdfBytes = Files.readAllBytes(pdf);
        String fileName = pdf.getFileName().toString();

        System.out.println("==== 开始上传 PDF: " + pdf.toAbsolutePath() + " (bytes=" + pdfBytes.length + ")");

        // Real MinerU path: requestUpload, uploadFile, poll, downloadZip, unpack
        ParsedDocument result = parser.parseStructured(pdfBytes, "application/pdf",
                Map.of(MinerUDocumentParser.OPT_SOURCE_FILE, fileName,
                        MinerUDocumentParser.OPT_DOCUMENT_ID, "doc-integration-test"));

        // metadata carries the zipUrl returned by MinerU. Reuse the real client and download another copy to disk
        Object zipUrlObj = result.metadata().get(MinerUDocumentParser.META_ZIP_URL);
        String zipUrl = zipUrlObj == null ? "" : zipUrlObj.toString();
        byte[] zipBytes = zipUrl.isEmpty() ? new byte[0] : minerUClient.downloadZip(zipUrl);

        // Write under .output/{run-timestamp-short-uuid}/ at the repository root. Each run gets its own subdirectory so nothing is overwritten
        Path runDir = resolveRepoRoot().resolve(OUT_DIR_NAME).resolve(newRunDirName());
        Files.createDirectories(runDir);
        Path zipPath = runDir.resolve("mineru-result.zip");
        Files.write(zipPath, zipBytes);
        List<String> extracted = extractZip(zipBytes, runDir);

        System.out.println("==== 输出目录: " + runDir.toAbsolutePath());
        System.out.println("==== MinerU 返回 zip 已保存: " + zipPath + " (bytes=" + zipBytes.length + ")");
        System.out.println("==== zip 已解压, 共 " + extracted.size() + " 个文件:");
        extracted.forEach(p -> System.out.println("    " + p));
        System.out.println("==== metadata: " + result.metadata());

        // Print MinerU's original markdown (the .md file inside the zip)
        System.out.println("==== MinerU 解析出的 Markdown(原始) ====");
        System.out.println(extractMarkdown(zipBytes));

        // Print the markdown rendered from ragent Blocks
        System.out.println("==== ragent Block 渲染 Markdown ====");
        System.out.println(BlockTextRenderer.render(result.blocks()));
        System.out.println("==== Block 数量: " + result.blocks().size());
    }

    /**
     * Repository root: walk up from the test class output (target/test-classes) and take the topmost directory that still contains pom.xml (the aggregator root). Works for IDE and mvn. Fall back to user.dir if none is found
     */
    private static Path resolveRepoRoot() {
        Path fallback = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        try {
            Path dir = Paths.get(MinerUPdfUploadFlowTest.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            Path root = fallback;
            for (Path p = dir; p != null; p = p.getParent()) {
                if (Files.exists(p.resolve("pom.xml"))) {
                    root = p;
                }
            }
            return root;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * One subdirectory name per run: timestamp plus a short uuid, sortable and collision-free
     */
    private static String newRunDirName() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        return "run-" + ts + "-" + shortId;
    }

    /**
     * Unpack every zip entry into outDir and return the relative paths extracted (directory entries are skipped)
     */
    private static List<String> extractZip(byte[] zipBytes, Path outDir) throws IOException {
        List<String> entries = new ArrayList<>();
        if (zipBytes == null || zipBytes.length == 0) {
            return entries;
        }
        Path outRoot = outDir.toAbsolutePath().normalize();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path target = outRoot.resolve(entry.getName()).normalize();
                // Zip-slip guard: the extract target must stay inside outDir
                if (!target.startsWith(outRoot)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.write(target, zin.readAllBytes());
                entries.add(outRoot.relativize(target).toString());
            }
        }
        return entries;
    }

    /**
     * Extract the text of the first .md entry from the zip bytes
     */
    private static String extractMarkdown(byte[] zipBytes) throws IOException {
        if (zipBytes == null || zipBytes.length == 0) {
            return "(zip 为空)";
        }
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".md")) {
                    return new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return "(zip 中未找到 .md 文件)";
    }
}
