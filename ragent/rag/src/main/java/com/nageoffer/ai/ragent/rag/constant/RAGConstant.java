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

package com.nageoffer.ai.ragent.rag.constant;

/**
 * RAG system constants
 *
 * <p>
 * Constants used by the RAG (Retrieval-Augmented Generation) system, including:
 * <ul>
 *   <li>Intent-recognition thresholds and limits</li>
 *   <li>Query-rewrite prompt templates</li>
 *   <li>RAG answer prompt templates</li>
 *   <li>System-dialogue prompt templates</li>
 *   <li>......</li>
 * </ul>
 * </p>
 *
 * <p>
 * These constants control RAG behavior and generation quality: intent filtering, query optimization,
 * document retrieval, and question answering
 * </p>
 */
public class RAGConstant {

    /**
     * Minimum intent-recognition score
     * <p>
     * Below this score the query is treated as off-topic and does not enter RAG retrieval
     * </p>
     */
    public static final double INTENT_MIN_SCORE = 0.35;

    /**
     * Maximum number of intents that may join a single query
     * Prevents pulling too many collections and hurting performance
     */
    public static final int MAX_INTENT_COUNT = 3;

    /**
     * Placeholder key for multi-channel retrieval
     * <p>
     * When intent recognition returns nothing, this key is the placeholder in the intentChunks map
     * Callers use only the map values and do not care about the key
     * </p>
     */
    public static final String MULTI_CHANNEL_KEY = "multi_channel";

    /**
     * Intent-recognition prompt template path (serial mode)
     * Sends every intent node to the LLM in one call
     */
    public static final String INTENT_CLASSIFIER_PROMPT_PATH = "prompt/intent-classifier.st";

    /**
     * Guided Q&A prompt template path
     * Builds the option prompt for guided Q&A
     */
    public static final String GUIDANCE_PROMPT_PATH = "prompt/guidance-prompt.st";

    /**
     * Ambiguity-confirmation prompt template path
     * On a borderline case, asks the LLM to confirm whether a category ambiguity exists
     */
    public static final String GUIDANCE_AMBIGUITY_CHECK_PROMPT_PATH = "prompt/guidance-ambiguity-check.st";


    /**
     * Query rewrite and multi-question split prompt template path
     * Must return both the rewritten single query and the sub-question list
     */
    public static final String QUERY_REWRITE_AND_SPLIT_PROMPT_PATH = "prompt/user-question-rewrite.st";


    /**
     * Conversation title prompt template path
     * {@code {title_max_chars}} and {@code {question}} control title length and the input question
     */
    public static final String CONVERSATION_TITLE_PROMPT_PATH = "prompt/conversation-title.st";


    /**
     * Inline citation rules for answers grounded in knowledge
     * <p>
     * Appended by the prompt orchestrator only when {@code rag.citation.enabled=true} and knowledge-base context exists
     */
    public static final String ANSWER_CITATION_RULES_PROMPT_PATH = "prompt/answer-citation-rules.st";


    // ==================== Context formatting templates (one file, many sections) ====================

    /**
     * Context formatting template file path
     * <p>
     * Holds every section needed to format context, separated by {@code --- section: name ---},
     * and rendered with {@code PromptTemplateLoader.renderSection(path, section, slots)}
     */
    public static final String CONTEXT_FORMAT_PATH = "prompt/context-format.st";
}
