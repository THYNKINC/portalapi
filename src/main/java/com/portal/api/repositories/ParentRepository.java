package com.portal.api.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.portal.api.model.Parent;

public interface ParentRepository extends MongoRepository<Parent, String> {
    // Additional custom query methods can be defined here if needed
}