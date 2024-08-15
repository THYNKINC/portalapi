package com.portal.api.services;

import com.portal.api.model.ImportJob;
import com.portal.api.repositories.ImportJobRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ImportJobService {

    private final ImportJobRepository importJobRepository;

    public ImportJobService(ImportJobRepository importJobRepository) {
        this.importJobRepository = importJobRepository;
    }

    public List<ImportJob> getImportJobs() {
        return importJobRepository.findAll();
    }
}
