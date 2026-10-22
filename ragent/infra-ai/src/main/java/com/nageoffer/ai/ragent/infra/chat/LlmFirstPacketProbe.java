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

package com.nageoffer.ai.ragent.infra.chat;

import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Splits awaitFirstPacket into its own bean so AOP can record TTFT traces
 * <p>
 * Spring AOP does not intercept intra-class self-calls, so RoutingLLMService must call
 * this method on an external bean for {@code @RagTraceNode} to apply
 */
@Component
public class LlmFirstPacketProbe {

    @RagTraceNode(name = "llm-first-packet", type = "LLM_TTFT")
    public ProbeStreamBridge.ProbeResult awaitFirstPacket(ProbeStreamBridge bridge,
                                                          long timeout,
                                                          TimeUnit unit) throws InterruptedException {
        return bridge.awaitFirstPacket(timeout, unit);
    }
}
