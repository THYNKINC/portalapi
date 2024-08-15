package com.portal.api.repositories;

import com.portal.api.model.Coach;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CoachRepository extends MongoRepository<Coach, String> {
    Optional<Coach> findOneByCohortsId(String cohortId);
}
