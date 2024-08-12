package com.portal.api.repositories;

import com.portal.api.model.Coach;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CoachRepository extends MongoRepository<Coach, String> {
    Coach findOneByCohortsId(String cohortId);
}
