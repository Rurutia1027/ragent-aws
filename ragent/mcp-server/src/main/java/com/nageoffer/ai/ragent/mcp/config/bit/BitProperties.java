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

package com.nageoffer.ai.ragent.mcp.config.bit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * BitSelect business-database settings
 * <p>
 * Uses its own prefix instead of spring.datasource: the same process also hosts the enterprise-assistant tools,
 * and manages the business-database connection and table-creation order on its own
 */
@Data
@ConfigurationProperties(prefix = "ragent.bit")
public class BitProperties {

    private Datasource datasource = new Datasource();

    private PendingOrder pendingOrder = new PendingOrder();

    @Data
    public static class Datasource {

        private String url;

        private String username;

        private String password;

        /**
         * Maintenance database connected while creating the database. CREATE DATABASE cannot run against a target database that does not exist yet
         */
        private String maintenanceDb = "postgres";
    }

    /**
     * Timeout release of unpaid orders
     * <p>
     * Stock is deducted and the coupon is redeemed the moment the order is created. Holding them without giving them back permanently swallows both
     */
    @Data
    public static class PendingOrder {

        /**
         * The demo uses a short value so an automatic cancel is visible during one demo. A real store uses 30 minutes
         */
        private Duration timeout = Duration.ofMinutes(5);
    }
}
