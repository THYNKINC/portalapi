package com.portal.api.repositories;

import com.portal.api.model.Delegate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DelegateRepository extends MongoRepository<Delegate, String> {

    Page<Delegate> findByType(String type, Pageable pageable);

    Delegate findOneByChildrenUsername(String username);

    Delegate findOneByChildrenUsernameIgnoreCase(String username);
}
