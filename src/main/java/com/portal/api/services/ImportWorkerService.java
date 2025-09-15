package com.portal.api.services;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.portal.api.dto.request.CreateCohortUserRequest;
import com.portal.api.dto.response.ImportStatus;
import com.portal.api.dto.response.RegisterUserStatus;
import com.portal.api.model.Child;
import com.portal.api.model.Cohort;
import com.portal.api.model.Delegate;
import com.portal.api.model.ImportJob;
import com.portal.api.repositories.DelegateRepository;
import com.portal.api.repositories.ImportJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.*;

@Slf4j
@Service
public class ImportWorkerService {

    private final ImportJobRepository importJobRepository;

    private final GameApiService gameApiService;

    private final DelegateRepository delegateRepository;

    public ImportWorkerService(ImportJobRepository importJobRepository, GameApiService gameApiService, DelegateRepository delegateRepository) {
        this.importJobRepository = importJobRepository;
        this.gameApiService = gameApiService;
        this.delegateRepository = delegateRepository;
    }

    @Async
    public void processUsersAsync(byte[] fileContent, String originalFilename, Cohort cohort, Delegate coach, String bearerToken, String jobId) {

        ImportJob job = importJobRepository.findById(jobId).orElse(new ImportJob());

        List<CreateCohortUserRequest> users = parseCsv(fileContent, originalFilename);
        if (users.isEmpty()) {
            job.setStatus("failed");
            job.setError("CSV parsing failed");
            job.setUsers(new ArrayList<>());
            importJobRepository.save(job);
            return;
        }

        List<RegisterUserStatus> result = gameApiService.registerMultipleUsers(users, bearerToken);

        if (result.isEmpty()) {
            job.setStatus(ImportStatus.FAILED);
            job.setError("No users where registered");
            job.setUsers(new ArrayList<>());
            importJobRepository.save(job);
            return;
        }

        boolean allFailed = result.stream().allMatch(user -> user.getImportStatus().getStatus().equals(ImportStatus.FAILED));
        if (allFailed) {
            job.setStatus(ImportStatus.FAILED);
            job.setError("No users where registered");
            job.setUsers(new ArrayList<>());
            importJobRepository.save(job);
            return;
        }

        List<RegisterUserStatus> registeredUsers = result
                .stream()
                .filter(user -> user.getImportStatus().getStatus().equals(ImportStatus.REGISTERED))
                .toList();

        for (RegisterUserStatus user : registeredUsers) {
            Child child = addToCohort(user, cohort);
            coach.addChild(child);
        }

        delegateRepository.save(coach);

        List<RegisterUserStatus> failedRegistrations = result
                .stream()
                .filter(user -> user.getImportStatus().getStatus().equals(ImportStatus.FAILED))
                .toList();

        if (failedRegistrations.isEmpty()) {
            job.setStatus(ImportStatus.COMPLETED);
            job.setUsers(result);
            importJobRepository.save(job);
        } else {
            job.setStatus(ImportStatus.COMPLETED_WITH_ERRORS);
            job.setUsers(result);
            importJobRepository.save(job);
        }
    }


    private List<CreateCohortUserRequest> parseCsv(byte[] fileContent, String originalFilename) {

        try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(fileContent))) {

            CsvToBean<CreateCohortUserRequest> csvToBean = new CsvToBeanBuilder<CreateCohortUserRequest>(reader)
                    .withType(CreateCohortUserRequest.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            return csvToBean.parse();

        } catch (Exception e) {
            log.warn("Error parsing CSV file: {}", originalFilename, e);
            return Collections.emptyList();
        }
    }

    private Child addToCohort(RegisterUserStatus user, Cohort cohort) {
        Child child = new Child();
        child.setFirstName(user.getFirstName());
        child.setLastName(user.getLastName());
        child.setUsername(user.getUsername());
        child.setDob(user.getDob());
        child.setSchool(user.getSchool());
        child.setClassName(user.getClassName());
        child.setGender(child.getGender());
        child.setGrade(user.getGrade());
        child.setCreatedDate(new Date());
        child.setDiagnosis(user.getDiagnosis());
        child.setProvider(user.getProvider());
        child.setGroup(user.getGroup());
        child.setUpdatedDate(new Date());
        child.setHeadsetId(null);
        child.setHeadsetType(null);
        child.setStartDate(null);
        child.setLocked(false);
        child.setDropped(false);
        child.setDroppedTime(null);
        child.setParentFirstName(user.getParentFirstName());
        child.setParentLastName(user.getParentLastName());
        child.setEmail(user.getEmail());
        child.setLabels(Map.of("cohort", cohort.getId()));

        return child;
    }

}
