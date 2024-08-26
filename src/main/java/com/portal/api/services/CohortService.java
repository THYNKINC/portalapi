package com.portal.api.services;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.portal.api.dto.request.CreateCohortRequest;
import com.portal.api.dto.request.CreateUserRequest;
import com.portal.api.dto.response.RegisterUserStatus;
import com.portal.api.model.Child;
import com.portal.api.model.Cohort;
import com.portal.api.model.Delegate;
import com.portal.api.model.ImportJob;
import com.portal.api.repositories.CohortsRepository;
import com.portal.api.repositories.DelegateRepository;
import com.portal.api.repositories.ImportJobRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.util.*;

@Service
public class CohortService {

    private final GameApiService gameApiService;

    private final DelegateRepository delegateRepository;

    private final ImportJobRepository importJobRepository;

    private final CohortsRepository cohortsRepository;

    public CohortService(GameApiService gameApiService, DelegateRepository delegateRepository, ImportJobRepository importJobRepository, CohortsRepository cohortsRepository) {
        this.gameApiService = gameApiService;
        this.delegateRepository = delegateRepository;
        this.importJobRepository = importJobRepository;
        this.cohortsRepository = cohortsRepository;
    }

    public void processUsersCsv(MultipartFile file, String coachUsername, String cohortId, String bearerToken) {

        Optional<Delegate> coachOptional = delegateRepository.findById(coachUsername);
        if (coachOptional.isEmpty()) {
            return;
        }

        Optional<Cohort> cohortOptional = cohortsRepository.findById(cohortId);
        if (cohortOptional.isEmpty()) {
            return;
        }

        Delegate coach = coachOptional.get();
        Cohort cohort = cohortOptional.get();

        ImportJob job = new ImportJob();
        job.setCohortId(cohortId);
        job.setStatus("running");

        importJobRepository.save(job);

        processUsersAsync(file, cohort, coach, bearerToken, job);
    }

    @Async
    public void processUsersAsync(MultipartFile file, Cohort cohort, Delegate coach, String bearerToken, ImportJob job) {

        List<CreateUserRequest> users = parseCsv(file);
        if (users.isEmpty()) {
            job.setStatus("failed");
            job.setError("CSV parsing failed");
            job.setUsers(new ArrayList<>());
            importJobRepository.save(job);
            return;
        }

        List<RegisterUserStatus> result = gameApiService.registerMultipleUsers(users, bearerToken);

        if (result.isEmpty()) {
            job.setStatus("failed");
            job.setError("No users where registered");
            job.setUsers(new ArrayList<>());
            importJobRepository.save(job);
            return;
        }

        List<RegisterUserStatus> registeredUsers = result
                .stream()
                .filter(user -> user.getStatus().equals("registered"))
                .toList();

        for (RegisterUserStatus user : registeredUsers) {
            Child child = addToCohort(user, cohort);
            coach.addChild(child);
        }

        delegateRepository.save(coach);

        job.setStatus("completed");
        job.setUsers(registeredUsers);
        importJobRepository.save(job);
    }

    public List<Cohort> getCohorts() {
        return cohortsRepository.findAll();
    }

    public Cohort createCohort(CreateCohortRequest createCohortRequest, String coachUsername) {

        Cohort cohort = new Cohort();
        cohort.setName(createCohortRequest.getName());
        cohort.setDescription(createCohortRequest.getDescription());
        cohort.setCoachUsername(coachUsername);

        cohortsRepository.save(cohort);

        return cohort;
    }

    private Delegate getCoach(String coachId) {
        Optional<Delegate> coachOptional = delegateRepository.findById(coachId);
        if (coachOptional.isEmpty()) {
            throw new ResourceAccessException("Coach does not exist");
        }
        return coachOptional.get();
    }

    public Cohort update(CreateCohortRequest updateCohortRequest, String id, String coachUsername) {
        Optional<Cohort> cohortOptional = cohortsRepository.findById(id);
        if (cohortOptional.isEmpty()) {
            throw new ResourceAccessException("Cohort does not exist");
        }

        Cohort cohort = cohortOptional.get();
        cohort.setName(updateCohortRequest.getName());
        cohort.setDescription(updateCohortRequest.getDescription());

        cohortsRepository.save(cohort);

        return cohort;
    }

    public void delete(String cohortId) {
        Optional<Cohort> cohortOptional = cohortsRepository.findById(cohortId);
        if (cohortOptional.isEmpty()) {
            throw new ResourceAccessException("Cohort does not exist");
        }

        Cohort cohort = cohortOptional.get();
        cohortsRepository.delete(cohort);
    }


    public Child addUserToCohort(CreateUserRequest createUserRequest, String cohortId, String coachUsername) {
        Delegate coach = getCoach(coachUsername);

        Optional<Cohort> cohortOptional = cohortsRepository.findById(cohortId);
        if (cohortOptional.isEmpty()) {
            throw new ResourceAccessException("Cohort does not exist");
        }

        Cohort cohort = cohortOptional.get();

        Child child = addToCohort(createUserRequest, cohort);
        coach.addChild(child);

        delegateRepository.save(coach);

        return child;
    }

    private List<CreateUserRequest> parseCsv(MultipartFile file) {

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream())) {

            CsvToBean<CreateUserRequest> csvToBean = new CsvToBeanBuilder<CreateUserRequest>(reader)
                    .withType(CreateUserRequest.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            return csvToBean.parse();

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Child addToCohort(RegisterUserStatus user, Cohort cohort) {
        Child child = new Child();
        child.setFirstName(user.getFirstName());
        child.setLastName(user.getLastName());
        child.setUsername(user.getUsername());
        child.setDob("");
        child.setSchool(user.getSchool());
        child.setClassName(user.getClassName());
        child.setGender(child.getGender());
        child.setGrade(user.getGrade());
        child.setCreatedDate(new Date());
        child.setLabels(Map.of("cohort", cohort.getId()));

        return child;
    }

    private Child addToCohort(CreateUserRequest user, Cohort cohort) {
        Child child = new Child();
        child.setFirstName(user.getFirstName());
        child.setLastName(user.getLastName());
        child.setUsername(user.getUsername());
        child.setDob("");
        child.setSchool(user.getSchool());
        child.setClassName(user.getClassName());
        child.setGender(child.getGender());
        child.setGrade(user.getGrade());
        child.setCreatedDate(new Date());
        child.setLabels(Map.of("cohort", cohort.getId()));

        return child;
    }
}
