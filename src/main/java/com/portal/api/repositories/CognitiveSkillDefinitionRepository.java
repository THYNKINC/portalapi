package com.portal.api.repositories;

import com.portal.api.model.CognitiveSkillDefinitionContainer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CognitiveSkillDefinitionRepository extends MongoRepository<CognitiveSkillDefinitionContainer, String> {
}
