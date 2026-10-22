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

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Create the database and tables at startup
 * <p>
 * This module owns the schema and runs it on every startup, so it can only be non-destructive. Data belongs to the initializer's BizDataInitMain,
 * which is idempotent by wiping and reloading. Putting that on the startup path would wipe orders on every restart
 */
@Slf4j
@RequiredArgsConstructor
public class BitSchemaInitializer {

    /**
     * Bump this number when bit-schema.sql changes
     */
    static final int SCHEMA_VERSION = 7;

    private static final String SCHEMA_SCRIPT = "db/bit-schema.sql";

    private static final String URL_PREFIX = "jdbc:postgresql://";

    /**
     * The database name is spliced into CREATE DATABASE. DDL cannot use a placeholder, so the allowed values are locked down first
     */
    private static final Pattern DATABASE_NAME = Pattern.compile("[A-Za-z0-9_]{1,63}");

    /**
     * The version table itself is not written in bit-schema.sql: it is the gate that decides whether that script runs, so it must exist before the script
     */
    private static final String VERSION_TABLE_DDL = """
            CREATE TABLE IF NOT EXISTS t_schema_version (
                version    INT PRIMARY KEY,
                applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
            )""";

    private final BitProperties.Datasource datasource;

    public void initialize() {
        ParsedUrl parsed = parseUrl(datasource.getUrl());
        ensureDatabase(parsed);
        applySchema(parsed);
    }

    /**
     * Create the database if it is missing. That step must autocommit. CREATE DATABASE errors inside any transaction block
     */
    private void ensureDatabase(ParsedUrl parsed) {
        String maintenanceUrl = parsed.withDatabase(resolveMaintenanceDb());
        try (Connection connection = connect(maintenanceUrl)) {
            if (databaseExists(connection, parsed.database())) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE \"" + parsed.database() + "\"");
            }
            log.info("比特严选业务库已创建, database={}", parsed.database());
        } catch (SQLException e) {
            // When two processes start together the later one collides, which means the database already exists
            if ("42P04".equals(e.getSQLState())) {
                return;
            }
            throw new IllegalStateException(explain(e, parsed), e);
        }
    }

    private boolean databaseExists(Connection connection, String database) throws SQLException {
        try (PreparedStatement statement = connection
                .prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            statement.setString(1, database);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * Three branches: no row runs the full DDL, an equal version skips, a mismatch refuses to start
     * <p>
     * Dropping DROP + CREATE has to put back the case it used to block. If the schema changed and the database is still old, CREATE IF NOT EXISTS
     * skips silently, and the error finally shows up as "some column does not exist" far from the cause
     */
    private void applySchema(ParsedUrl parsed) {
        try (Connection connection = connect(datasource.getUrl())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(VERSION_TABLE_DDL);
            }
            Integer current = readVersion(connection);
            if (current != null && current == SCHEMA_VERSION) {
                return;
            }
            if (current != null) {
                throw new IllegalStateException(versionMismatch(current, parsed.database()));
            }
            createSchema(connection);
            log.info("比特严选业务库表结构已建, database={}, version={}", parsed.database(), SCHEMA_VERSION);
        } catch (SQLException e) {
            throw new IllegalStateException(explain(e, parsed), e);
        }
    }

    /**
     * Creating tables and writing the version share one transaction. PostgreSQL DDL rolls back, so a failure midway does not leave a half set of tables with one version row
     */
    private void createSchema(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ClassPathResource(SCHEMA_SCRIPT), StandardCharsets.UTF_8));
            try (PreparedStatement statement = connection
                    .prepareStatement("INSERT INTO t_schema_version (version) VALUES (?)")) {
                statement.setInt(1, SCHEMA_VERSION);
                statement.executeUpdate();
            }
            connection.commit();
        } catch (RuntimeException | SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private Integer readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT max(version) FROM t_schema_version")) {
            if (!resultSet.next()) {
                return null;
            }
            int version = resultSet.getInt(1);
            return resultSet.wasNull() ? null : version;
        }
    }

    private Connection connect(String url) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", StrUtil.emptyIfNull(datasource.getUsername()));
        properties.setProperty("password", StrUtil.emptyIfNull(datasource.getPassword()));
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("loginTimeout", "5");
        return DriverManager.getConnection(url, properties);
    }

    private String resolveMaintenanceDb() {
        String maintenanceDb = StrUtil.blankToDefault(datasource.getMaintenanceDb(), "postgres");
        if (!DATABASE_NAME.matcher(maintenanceDb).matches()) {
            throw new IllegalStateException("ragent.bit.datasource.maintenance-db may contain only letters, digits, and underscores: " + maintenanceDb);
        }
        return maintenanceDb;
    }

    /**
     * Native errors sit far from the cause. Each one is replaced with a next step that can actually be followed
     */
    static String explain(SQLException e, ParsedUrl parsed) {
        String state = StrUtil.emptyIfNull(e.getSQLState());
        if ("42501".equals(state)) {
            return "当前数据库账号无 CREATEDB 权限，请手动执行 createdb %s 后重启".formatted(parsed.database());
        }
        if (state.startsWith("08") || "28P01".equals(state) || "28000".equals(state)) {
            return ("连不上 PostgreSQL（%s），请启动数据库并确认 ragent.bit.datasource 的地址和账号密码")
                    .formatted(parsed.url());
        }
        return "比特严选业务库初始化失败: " + e.getMessage();
    }

    static String versionMismatch(int current, String database) {
        return ("业务库结构版本 %d，代码要求 %d。本项目不做增量迁移，"
                + "请执行 dropdb %s 后重启，再跑一次 BizDataInitMain 灌数据")
                .formatted(current, SCHEMA_VERSION, database);
    }

    /**
     * Split the database name out of the JDBC URL. Creating the database connects again, against the maintenance database
     */
    static ParsedUrl parseUrl(String url) {
        if (!StrUtil.startWith(url, URL_PREFIX)) {
            throw new IllegalStateException("ragent.bit.datasource.url must be " + URL_PREFIX + " prefix: " + url);
        }
        String rest = url.substring(URL_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            throw new IllegalStateException("Could not read a database name from ragent.bit.datasource.url: " + url);
        }
        String tail = rest.substring(slash + 1);
        int question = tail.indexOf('?');
        String database = question < 0 ? tail : tail.substring(0, question);
        if (!DATABASE_NAME.matcher(database).matches()) {
            throw new IllegalStateException("Business database name may contain only letters, digits, and underscores: " + database);
        }
        return new ParsedUrl(rest.substring(0, slash), database, question < 0 ? "" : tail.substring(question + 1));
    }

    record ParsedUrl(String authority, String database, String query) {

        String withDatabase(String other) {
            return URL_PREFIX + authority + "/" + other + (query.isEmpty() ? "" : "?" + query);
        }

        String url() {
            return withDatabase(database);
        }
    }
}
