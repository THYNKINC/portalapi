package com.portal.api.services;

import com.portal.api.model.ImportJob;
import com.portal.api.repositories.ImportJobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ImportJobService {

    private final ImportJobRepository importJobRepository;

    public ImportJobService(ImportJobRepository importJobRepository) {
        this.importJobRepository = importJobRepository;
    }

    public Page<ImportJob> getImportJobs(Pageable pageRequest) {
        return importJobRepository.findAll(pageRequest);
    }

    public ImportJob findById(String jobId) {
        return importJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("ImportJob with ID " + jobId + " not found"));
    }
}
