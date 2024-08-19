package com.portal.api.services;

import com.portal.api.model.CognitiveSkillDefinitionContainer;
import com.portal.api.repositories.CognitiveSkillDefinitionRepository;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

@Service
public class CognitiveSkillDefinitionService {

    private final CognitiveSkillDefinitionRepository repository;

    public CognitiveSkillDefinitionService(CognitiveSkillDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<CognitiveSkillDefinitionContainer> getAll() {
        return repository.findAll();
    }

    public Optional<CognitiveSkillDefinitionContainer> getById(String id) {
        return repository.findById(id);
    }

    public CognitiveSkillDefinitionContainer update(String id, @Valid CognitiveSkillDefinitionContainer container) {
        container.setId(id);
        return repository.save(container);
    }
}
