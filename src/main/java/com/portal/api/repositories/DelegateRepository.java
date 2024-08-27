package com.portal.api.repositories;

import com.portal.api.model.Delegate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DelegateRepository extends MongoRepository<Delegate, String> {

	Page<Delegate> findByFirstNameIgnoreCaseStartingWithOrLastNameIgnoreCaseStartingWith(String partialFirstName, String partialLastName, Pageable pageable);

	Page<Delegate> findByType(String type, Pageable pageable);
}