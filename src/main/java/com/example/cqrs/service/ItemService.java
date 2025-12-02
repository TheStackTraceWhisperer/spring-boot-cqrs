package com.example.cqrs.service;

import com.example.cqrs.entity.Item;
import com.example.cqrs.repository.ItemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service layer demonstrating read-write splitting with @Transactional annotations.
 */
@Service
public class ItemService {

    private final ItemRepository itemRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Creates an item using the read-write data source.
     */
    @Transactional(readOnly = false)
    public Item createItem(String name) {
        return itemRepository.save(new Item(name));
    }

    /**
     * Retrieves all items using the read-only data source.
     */
    @Transactional(readOnly = true)
    public List<Item> getAllItems() {
        return itemRepository.findAll();
    }

    /**
     * Gets the JDBC URL of the current connection for testing purposes.
     * Uses the Hibernate session's JDBC connection to verify correct data source routing.
     */
    @Transactional(readOnly = false)
    public String getReadWriteConnectionUrl() throws SQLException {
        AtomicReference<String> url = new AtomicReference<>();
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> url.set(connection.getMetaData().getURL()));
        return url.get();
    }

    /**
     * Gets the JDBC URL of the current read-only connection for testing purposes.
     * Uses the Hibernate session's JDBC connection to verify correct data source routing.
     */
    @Transactional(readOnly = true)
    public String getReadOnlyConnectionUrl() throws SQLException {
        AtomicReference<String> url = new AtomicReference<>();
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> url.set(connection.getMetaData().getURL()));
        return url.get();
    }
}
