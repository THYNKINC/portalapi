package com.portal.api.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.portal.api.model.Parent;

public interface ParentRepository extends MongoRepository<Parent, String> {

	Page<Parent> findByFirstNameIgnoreCaseStartingWithOrLastNameIgnoreCaseStartingWith(String partialFirstName, String partialLastName, Pageable pageable);
	Parent findOneByChildrenUsername(String username);
}