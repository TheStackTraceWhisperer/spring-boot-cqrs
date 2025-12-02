package com.example.cqrs.repository;

import com.example.cqrs.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA Repository for Item entity.
 */
@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
}
