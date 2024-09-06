package com.portal.api.repositories;

import com.portal.api.model.Cohort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CohortsRepository extends MongoRepository<Cohort, String> {
    List<Cohort> findAllByCoachUsername(String username);
}
