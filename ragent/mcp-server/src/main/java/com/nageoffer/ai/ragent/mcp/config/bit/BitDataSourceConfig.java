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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.nageoffer.ai.ragent.mcp.dao.handler.BitMetaObjectHandler;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * Data source for the BitSelect business database
 * <p>
 * At startup, initialize the business-database schema first, then create the pool
 */
@Configuration
@EnableConfigurationProperties(BitProperties.class)
@MapperScan("com.nageoffer.ai.ragent.mcp.dao.mapper")
public class BitDataSourceConfig {

    /**
     * Database and table creation is triggered here, not hung off a @PostConstruct
     * <p>
     * Creating the database must connect to the maintenance database first, while the pool targets the destination database. Reversing that is "connect before the database exists".
     * Written in the bean method, order is guaranteed by call order and does not depend on the relative order of init callbacks
     */
    @Bean
    public DataSource bitDataSource(BitProperties properties) {
        new BitSchemaInitializer(properties.getDatasource()).initialize();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("bit-pool");
        dataSource.setJdbcUrl(properties.getDatasource().getUrl());
        dataSource.setUsername(properties.getDatasource().getUsername());
        dataSource.setPassword(properties.getDatasource().getPassword());
        dataSource.setMaximumPoolSize(8);
        return dataSource;
    }

    /**
     * SqlSessionFactory is assembled here instead of relying on MP auto-configuration
     * <p>
     * Auto-configuration looks at spring.datasource, while this process's data source lives under ragent.bit.datasource
     * and this configuration initializes the schema before creating the pool
     */
    @Bean
    public SqlSessionFactory bitSqlSessionFactory(DataSource bitDataSource) throws Exception {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(new BitMetaObjectHandler());

        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(bitDataSource);
        factory.setConfiguration(new MybatisConfiguration());
        factory.setGlobalConfig(globalConfig);
        return factory.getObject();
    }

    @Bean
    public SqlSessionTemplate bitSqlSessionTemplate(SqlSessionFactory bitSqlSessionFactory) {
        return new SqlSessionTemplate(bitSqlSessionFactory);
    }

    @Bean
    public PlatformTransactionManager bitTransactionManager(DataSource bitDataSource) {
        return new DataSourceTransactionManager(bitDataSource);
    }

    /**
     * Write tools use programmatic transactions, not @Transactional
     * <p>
     * Those executors declare their own @Bean methods, and a transaction annotation would wrap them in a proxy.
     * In a teaching project, seeing which line the transaction opens and closes on is worth more than saving a few lines
     */
    @Bean
    public TransactionTemplate bitTransactionTemplate(PlatformTransactionManager bitTransactionManager) {
        return new TransactionTemplate(bitTransactionManager);
    }
}
