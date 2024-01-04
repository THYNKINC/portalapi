package com.portal.api.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.portal.api.model.Delegate;

public interface DelegateRepository extends MongoRepository<Delegate, String> {

	Page<Delegate> findByFirstNameIgnoreCaseStartingWithOrLastNameIgnoreCaseStartingWith(String partialFirstName, String partialLastName, Pageable pageable);
}