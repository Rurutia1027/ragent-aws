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

package com.nageoffer.ai.ragent.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI model configuration properties
 * Reads AI settings from the config file, including providers and model groups
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AIModelProperties {

    /**
     * AI provider configuration map
     * key: provider name, value: provider settings
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /**
     * Chat model group
     */
    private ModelGroup chat = new ModelGroup();

    /**
     * Embedding model group
     */
    private ModelGroup embedding = new ModelGroup();

    /**
     * Rerank model group
     */
    private ModelGroup rerank = new ModelGroup();

    /**
     * Vision-language model group (image to text, used while ingesting the knowledge base)
     */
    private ModelGroup vlm = new ModelGroup();

    /**
     * Model selection strategy
     */
    private Selection selection = new Selection();

    /**
     * Streaming response settings
     */
    private Stream stream = new Stream();

    /**
     * Model group settings
     * Holds the default model and the candidate list
     */
    @Data
    public static class ModelGroup {
        /**
         * Default model id (used by embedding/rerank/vlm; chat now uses tiers)
         */
        private String defaultModel;

        /**
         * Candidate model list
         * <p>
         * Under the chat group this degenerates to a physical model registry: it only records id to provider/model. Tier order is decided by tiers
         */
        private List<ModelCandidate> candidates = new ArrayList<>();

        /**
         * Default tier name (chat only)
         * The tier used when no Tier override is set (the fallback tier)
         */
        private String defaultTier;

        /**
         * Deep-thinking tier name (chat only)
         * The tier used when the user turns on deep thinking
         */
        private String deepThinkingTier;

        /**
         * Tier configuration map (chat only)
         * key: tier name (such as fast/standard/deep), value: that tier's candidates and timeout
         */
        private Map<String, TierConfig> tiers = new HashMap<>();
    }

    /**
     * Tier settings
     * Ordered candidates and timeout budget for one tier
     */
    @Data
    public static class TierConfig {

        /**
         * Ordered candidate model ids
         * Ids from the candidates registry. Order is the candidate order (local, not the global priority)
         */
        private List<String> candidates = new ArrayList<>();

        /**
         * Call timeout budget for this tier, in milliseconds. null means no extra limit
         */
        private Long timeoutMs;
    }

    /**
     * Model candidate settings
     * Detailed settings for one candidate model
     */
    @Data
    public static class ModelCandidate {

        /**
         * Unique model id
         */
        private String id;

        /**
         * Model provider name
         */
        private String provider;

        /**
         * Model name
         */
        private String model;

        /**
         * Model access URL
         */
        private String url;

        /**
         * Vector dimension (embedding models)
         */
        private Integer dimension;

        /**
         * Model priority. A smaller number is higher priority
         */
        private Integer priority = 100;

        /**
         * Whether this model is enabled
         */
        private Boolean enabled = true;

        /**
         * Whether chain-of-thought is supported
         */
        private Boolean supportsThinking = false;
    }

    /**
     * Provider settings
     * Basic connection info and endpoint configuration for a provider
     */
    @Data
    public static class ProviderConfig {

        /**
         * Provider base URL
         */
        private String url;

        /**
         * API key
         */
        private String apiKey;

        /**
         * Endpoint map
         * key: endpoint type, value: endpoint path
         */
        private Map<String, String> endpoints = new HashMap<>();
    }

    /**
     * Model selection strategy
     * Failover and circuit-breaker settings
     */
    @Data
    public static class Selection {

        /**
         * Failure threshold. Crossing it opens the circuit
         */
        private Integer failureThreshold = 2;

        /**
         * How long the circuit stays open, in milliseconds
         */
        private Long openDurationMs = 30000L;
    }

    /**
     * Streaming response settings
     * Parameters for streaming output
     */
    @Data
    public static class Stream {

        /**
         * Message chunk size
         */
        private Integer messageChunkSize = 5;
    }
}
