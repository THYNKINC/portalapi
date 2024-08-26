package com.portal.api.repositories;

import com.portal.api.model.Cohort;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CohortsRepository extends MongoRepository<Cohort, String> {
}
