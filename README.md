Technical Specification: Spring Boot Read-Write Splitting for MS SQL Server
Version: 1.0
Date: 2025-12-02
Target Framework: Spring Boot 3.5.x (Implemented using 3.4.0 forward-compatible APIs)
1. Executive Summary
This project implements a database interaction layer for a Spring Boot application that splits traffic between two distinct connection pools: a Read-Write (Primary) pool and a Read-Only (Secondary) pool.
The routing logic is transparent to the business layer, relying on Spring's @Transactional annotations to dynamically switch data sources. The backend database is Microsoft SQL Server, utilizing the ApplicationIntent=ReadOnly connection property for the secondary pool to support read-replica routing in high-availability environments.
2. Architecture Overview
2.1 High-Level Design
The system uses the AbstractRoutingDataSource pattern to act as a proxy between the EntityManagerFactory and the physical connection pools.
 * Business Service: Declares transaction boundaries using @Transactional(readOnly = boolean).
 * Transaction Manager: Binds the transaction state (Read-Only vs. Read-Write) to the current thread via TransactionSynchronizationManager.
 * Routing Data Source: Intercepts the connection request, checks the thread context, and delegates to the appropriate HikariCP pool.
 * Physical Pools:
   * Primary: Standard connection string.
   * Secondary: Standard connection string + ApplicationIntent=ReadOnly.
2.2 Component Diagram
graph TD
    ServiceLayer[Service Layer <br/> @Transactional] -->|Calls| Repo[JPA Repository]
    Repo -->|Uses| EM[Entity Manager]
    EM -->|Requests Conn| RoutingDS[AbstractRoutingDataSource]
    
    RoutingDS -- Checks Context --> TSM[Transaction Sync Manager]
    
    RoutingDS -->|ReadOnly = false| PoolRW[HikariCP: Read-Write]
    RoutingDS -->|ReadOnly = true| PoolRO[HikariCP: Read-Only]
    
    PoolRW -->|JDBC| DB_Primary[(MSSQL Primary)]
    PoolRO -->|JDBC + Intent=ReadOnly| DB_Secondary[(MSSQL Replica)]

3. Technology Stack
| Component | Technology | Version | Notes |
|---|---|---|---|
| Language | Java | 21 | LTS Version |
| Framework | Spring Boot | 3.5.6 (Target) | Uses Spring Data JPA |
| Database | MS SQL Server | 2022 | via Docker/Testcontainers |
| Driver | MSSQL JDBC | Latest | com.microsoft.sqlserver:mssql-jdbc |
| Pooling | HikariCP | Default | Spring Boot default provider |
| Testing | Testcontainers | Latest | Azure SQL / MSSQL Module |
4. Implementation Specifications
4.1 DataSource Configuration
The application must configure two separate DataSourceProperties beans to prevent property collision.
 * Primary Configuration Prefix: spring.datasource
 * Secondary Configuration Prefix: spring.datasource.readonly
Bean Definitions
 * readWriteDataSource: Created from spring.datasource.
 * readOnlyDataSource: Created from spring.datasource.readonly.
 * routingDataSource (Primary Bean): An extension of AbstractRoutingDataSource containing a map of the two pools above.
4.2 Routing Logic
The routing decision is made at the start of a transaction. The implementation of determineCurrentLookupKey() must follow this logic:
if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
    return DataSourceType.READ_ONLY;
} else {
    return DataSourceType.READ_WRITE;
}

4.3 Database Configuration (MS SQL Specifics)
To simulate production read-replica routing or to specifically tag read traffic, the secondary pool must append the specific SQL Server intent flag to the JDBC URL.
 * Primary URL Pattern: jdbc:sqlserver://<host>:<port>;databaseName=<db>
 * Secondary URL Pattern: jdbc:sqlserver://<host>:<port>;databaseName=<db>;ApplicationIntent=ReadOnly
5. Development & Testing Environment
5.1 Infrastructure as Code
The application does not rely on a locally installed SQL Server instance. It utilizes Testcontainers for both development (via main method bootstrapping) and integration testing.
 * Image: mcr.microsoft.com/mssql/server:2022-latest
 * License Acceptance: Must accept EULA in container config.
5.2 Integration Testing Strategy
Tests must verify that routing physically occurs, not just that the annotation is present.
 * Container Setup: Spin up a single MSSQL container.
 * Dynamic Property Injection:
   * Inject the standard JDBC URL into spring.datasource.url.
   * Inject the JDBC URL with ;ApplicationIntent=ReadOnly appended into spring.datasource.readonly.url.
 * Verification:
   * Execute a @Transactional(readOnly = false) block. Assert the underlying connection metadata URL does not contain the intent flag.
   * Execute a @Transactional(readOnly = true) block. Assert the underlying connection metadata URL does contain the intent flag.
6. Configuration Parameters
The application expects the following properties (resolved via application.properties or System Properties injected by Testcontainers):
| Property Key | Description | Default/Example |
|---|---|---|
| spring.datasource.url | Primary JDBC URL | jdbc:sqlserver://localhost... |
| spring.datasource.username | DB Username | sa |
| spring.datasource.password | DB Password | Secret123! |
| spring.datasource.readonly.url | Secondary JDBC URL | ...;ApplicationIntent=ReadOnly |
| spring.datasource.readonly.username | Secondary Username | sa |
| spring.datasource.hikari.maximum-pool-size | Max connections (RW) | 5 |
| spring.datasource.readonly.hikari.maximum-pool-size | Max connections (RO) | 5 |
7. Constraints & Assumptions
 * Transaction Consistency: Read-Only transactions may read stale data depending on SQL Server replication lag (in a real production multi-node scenario).
 * Transaction Context: Routing only works within a @Transactional boundary. Operations performed outside a transaction will default to the defaultTargetDataSource (configured as Read-Write).
 * Container Performance: Testcontainers requires a functional Docker environment.
