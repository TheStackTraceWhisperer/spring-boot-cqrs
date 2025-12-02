package com.example.cqrs;

import com.example.cqrs.entity.Item;
import com.example.cqrs.service.ItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for read-write splitting functionality using Testcontainers.
 */
@SpringBootTest
@Testcontainers
class ReadWriteRoutingIntegrationTest {

    @Container
    static MSSQLServerContainer<?> sqlServerContainer = new MSSQLServerContainer<>(
            "mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();

    @Autowired
    private ItemService itemService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String baseUrl = sqlServerContainer.getJdbcUrl();
        
        // Primary (Read-Write) URL - standard connection
        registry.add("spring.datasource.url", () -> baseUrl);
        registry.add("spring.datasource.username", sqlServerContainer::getUsername);
        registry.add("spring.datasource.password", sqlServerContainer::getPassword);
        
        // Secondary (Read-Only) URL - with ApplicationIntent=ReadOnly
        registry.add("spring.datasource.readonly.url", () -> baseUrl + ";ApplicationIntent=ReadOnly");
        registry.add("spring.datasource.readonly.username", sqlServerContainer::getUsername);
        registry.add("spring.datasource.readonly.password", sqlServerContainer::getPassword);
    }

    @Test
    void testReadWriteTransaction_ShouldUseReadWriteDataSource() throws SQLException {
        String url = itemService.getReadWriteConnectionUrl().toLowerCase();
        
        assertNotNull(url);
        assertFalse(url.contains("applicationintent=readonly"),
                "Read-write transaction should NOT use the ReadOnly intent flag");
    }

    @Test
    void testReadOnlyTransaction_ShouldUseReadOnlyDataSource() throws SQLException {
        String url = itemService.getReadOnlyConnectionUrl().toLowerCase();
        
        assertNotNull(url);
        assertTrue(url.contains("applicationintent=readonly"),
                "Read-only transaction should use the ReadOnly intent flag");
    }

    @Test
    void testCreateItem_ShouldPersistUsingReadWriteDataSource() {
        Item item = itemService.createItem("Test Item");
        
        assertNotNull(item);
        assertNotNull(item.getId());
        assertEquals("Test Item", item.getName());
    }

    @Test
    void testGetAllItems_ShouldRetrieveUsingReadOnlyDataSource() {
        // First create an item
        itemService.createItem("Item for Reading");
        
        // Then retrieve all items
        List<Item> items = itemService.getAllItems();
        
        assertNotNull(items);
        assertFalse(items.isEmpty());
    }

    @Test
    void testRoutingSwitchBetweenReadWriteAndReadOnly() throws SQLException {
        // First use read-write
        String rwUrl = itemService.getReadWriteConnectionUrl().toLowerCase();
        assertFalse(rwUrl.contains("applicationintent=readonly"),
                "First call should use read-write data source");
        
        // Then use read-only
        String roUrl = itemService.getReadOnlyConnectionUrl().toLowerCase();
        assertTrue(roUrl.contains("applicationintent=readonly"),
                "Second call should use read-only data source");
        
        // Back to read-write
        String rwUrl2 = itemService.getReadWriteConnectionUrl().toLowerCase();
        assertFalse(rwUrl2.contains("applicationintent=readonly"),
                "Third call should use read-write data source again");
    }
}
