package com.example.cqrs.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A routing data source that determines the current lookup key based on the transaction's read-only status.
 * Routes to READ_ONLY data source when the current transaction is read-only,
 * otherwise routes to READ_WRITE data source.
 */
public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return DataSourceType.READ_ONLY;
        } else {
            return DataSourceType.READ_WRITE;
        }
    }
}
