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

package com.nageoffer.ai.ragent.rag.controller;

import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.rag.controller.vo.GraphViewVO;
import com.nageoffer.ai.ragent.rag.core.graph.GraphQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Knowledge-graph visualization controller
 * Admin graph browsing: fetch a subgraph and search entity labels
 * <p>
 * When the graph channel is disabled, the service layer raises a business error and the frontend shows a "not enabled" hint. The route itself always exists
 */
@RestController
@RequestMapping("/admin/kg")
@RequiredArgsConstructor
public class GraphController {

    private final GraphQueryService graphQueryService;

    /**
     * Fetch a graph subgraph
     *
     * @param entity     start entity name; empty means the whole graph
     * @param collection knowledge-base collectionName; limit the view to that base. Empty means no limit
     * @param doc        document id; limit the view to that document. Takes priority over collection. Empty means no limit
     * @param depth      subgraph depth, default 2
     * @param limit      node cap, default 200
     */
    @GetMapping("/graph")
    public Result<GraphViewVO> graph(@RequestParam(required = false) String entity,
                                     @RequestParam(required = false) String collection,
                                     @RequestParam(required = false) String doc,
                                     @RequestParam(defaultValue = "2") int depth,
                                     @RequestParam(defaultValue = "200") int limit) {
        return Results.success(graphQueryService.getGraph(entity, collection, doc, depth, limit));
    }

    /**
     * Search entity labels for the visualization search box. An empty keyword returns popular labels
     */
    @GetMapping("/labels")
    public Result<List<String>> labels(@RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "50") int limit) {
        return Results.success(graphQueryService.searchEntities(keyword, limit));
    }
}
