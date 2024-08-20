package com.portal.api.repositories;

import com.portal.api.model.MissionDialog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MissionDialogRepository extends MongoRepository<MissionDialog, String> {
}