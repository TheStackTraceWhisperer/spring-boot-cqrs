package com.example.cqrs.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for read-write splitting data sources.
 * Creates two separate HikariCP connection pools and routes between them
 * based on the transaction's read-only status.
 */
@Configuration
public class DataSourceConfig {

    /**
     * Primary data source properties configuration.
     */
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties readWriteDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Read-only data source properties configuration.
     */
    @Bean
    @ConfigurationProperties("spring.datasource.readonly")
    public DataSourceProperties readOnlyDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Primary (read-write) HikariCP data source.
     */
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource readWriteDataSource(
            @Qualifier("readWriteDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * Secondary (read-only) HikariCP data source.
     */
    @Bean
    @ConfigurationProperties("spring.datasource.readonly.hikari")
    public HikariDataSource readOnlyDataSource(
            @Qualifier("readOnlyDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * Routing data source that switches between read-write and read-only pools.
     */
    @Bean
    public DataSource routingDataSource(
            @Qualifier("readWriteDataSource") DataSource readWriteDataSource,
            @Qualifier("readOnlyDataSource") DataSource readOnlyDataSource) {
        ReadWriteRoutingDataSource routingDataSource = new ReadWriteRoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put(DataSourceType.READ_WRITE, readWriteDataSource);
        dataSourceMap.put(DataSourceType.READ_ONLY, readOnlyDataSource);

        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(readWriteDataSource);

        return routingDataSource;
    }

    /**
     * Primary data source bean wrapped in LazyConnectionDataSourceProxy.
     * The proxy delays obtaining a connection until it's actually needed,
     * allowing the routing decision to be made after the transaction context is set.
     */
    @Primary
    @Bean
    public DataSource dataSource(@Qualifier("routingDataSource") DataSource routingDataSource) {
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }
}
