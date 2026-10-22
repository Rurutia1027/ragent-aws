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

package com.nageoffer.ai.ragent.rag.core.prompt;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt template loader.
 * Loads prompt template files from the classpath and fills template variables.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateLoader {

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> sectionCache = new ConcurrentHashMap<>();

    /**
     * Load the prompt template at the given path.
     *
     * @param path template file path; a classpath: prefix is supported
     * @return template content
     * @throws IllegalArgumentException when the path is blank
     * @throws IllegalStateException    when the template file is missing or cannot be read
     */
    public String load(String path) {
        if (StrUtil.isBlank(path)) {
            throw new IllegalArgumentException("Prompt template path is empty");
        }
        return cache.computeIfAbsent(path, this::readResource);
    }

    /**
     * Render a prompt template, replacing placeholders with values.
     *
     * @param path  template file path
     * @param slots placeholder map; key is the placeholder name, value is the replacement
     * @return rendered prompt text
     */
    public String render(String path, Map<String, String> slots) {
        String template = load(path);
        String filled = PromptTemplateUtils.fillSlots(template, slots);
        return PromptTemplateUtils.cleanupPrompt(filled);
    }

    /**
     * Load the raw content of one section in a template file.
     *
     * @param path    template file path
     * @param section section name (the name in {@code --- section: name ---})
     * @return raw template content of the section
     * @throws IllegalStateException when the section does not exist
     */
    public String loadSection(String path, String section) {
        Map<String, String> sections = sectionCache.computeIfAbsent(path, p -> {
            String content = load(p);
            return PromptTemplateUtils.parseSections(content);
        });
        String template = sections.get(section);
        if (template == null) {
            throw new IllegalStateException("Template section does not exist: " + path + " -> " + section);
        }
        return template;
    }

    /**
     * Render one section of a template file and fill placeholders.
     *
     * @param path    template file path
     * @param section section name
     * @param slots   placeholder map
     * @return rendered text
     */
    public String renderSection(String path, String section, Map<String, String> slots) {
        String template = loadSection(path, section);
        String filled = PromptTemplateUtils.fillSlots(template, slots);
        return PromptTemplateUtils.cleanupPrompt(filled);
    }

    /**
     * Read template content from a resource path.
     *
     * @param path template file path
     * @return template content
     * @throws IllegalStateException when the template file is missing or cannot be read
     */
    private String readResource(String path) {
        String location = path.startsWith("classpath:") ? path : "classpath:" + path;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Prompt template path does not exist: " + path);
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取提示模板失败，路径：{}", path, e);
            throw new IllegalStateException("Failed to read the prompt template, path: " + path, e);
        }
    }
}
