package com.portal.api.repositories;

import com.portal.api.model.ImportJob;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ImportJobRepository extends MongoRepository<ImportJob, String> {
}
