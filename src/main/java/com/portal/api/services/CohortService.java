package com.portal.api.services;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.portal.api.dto.request.CreateCohortRequest;
import com.portal.api.dto.request.CreateUserRequest;
import com.portal.api.dto.response.RegisterUserStatus;
import com.portal.api.model.Child;
import com.portal.api.model.Coach;
import com.portal.api.model.Cohort;
import com.portal.api.model.ImportJob;
import com.portal.api.repositories.CoachRepository;
import com.portal.api.repositories.ImportJobRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class CohortService {

    private final GameApiService gameApiService;

    private final CoachRepository coachRepository;

    private final ImportJobRepository importJobRepository;

    public CohortService(GameApiService gameApiService, CoachRepository coachRepository, ImportJobRepository importJobRepository) {
        this.gameApiService = gameApiService;
        this.coachRepository = coachRepository;
        this.importJobRepository = importJobRepository;
    }

    @Async
    public CompletableFuture<Boolean> processUsersCsv(MultipartFile file, String cohortId, String bearerToken) {

        List<CreateUserRequest> users = parseCsv(file);
        if (users.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        Coach coach = coachRepository.findOneByCohortsId(cohortId);

        if (coach == null) {
            return CompletableFuture.completedFuture(false);
        }

        if (coach.getCohorts() == null) {
            return CompletableFuture.completedFuture(false);
        }

        Optional<Cohort> cohortOptional = coach.getCohorts()
                .stream()
                .filter(cohort -> cohort.getId().equals(cohortId))
                .findFirst();

        if (cohortOptional.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        ImportJob job = new ImportJob();
        job.setCohortId(cohortId);
        job.setStatus("running");

        importJobRepository.save(job);

        List<RegisterUserStatus> result = gameApiService.registerMultipleUsers(users, bearerToken);

        if (result.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        List<RegisterUserStatus> registeredUsers = result
                .stream()
                .filter(user -> user.getStatus().equals("registered"))
                .toList();


        Cohort cohort = cohortOptional.get();
        for (RegisterUserStatus user : registeredUsers) {
            addChild(user, cohort);
        }

        coachRepository.save(coach);

        job.setStatus("completed");
        job.setUsers(registeredUsers);
        importJobRepository.save(job);

        return CompletableFuture.completedFuture(true);
    }

    public List<Cohort> getCohorts(String coachUsername) {

        Coach coach = getCoach(coachUsername);

        if (coach.getCohorts() == null) {
            return Collections.emptyList();
        }

        return coach.getCohorts();
    }

    public Cohort createCohort(CreateCohortRequest createCohortRequest, String coachUsername) {

        Coach coach = getCoach(coachUsername);

        Cohort cohort = new Cohort();
        cohort.setName(createCohortRequest.getName());
        cohort.setDescription(createCohortRequest.getDescription());

        coach.addCohort(cohort);

        coachRepository.save(coach);

        return cohort;
    }

    private Coach getCoach(String coachUsername) {
        Optional<Coach> coachOptional = coachRepository.findById(coachUsername);
        if (coachOptional.isEmpty()) {
            throw new ResourceAccessException("Coach does not exist");
        }
        return coachOptional.get();
    }

    public Cohort update(CreateCohortRequest updateCohortRequest, String id, String coachUsername) {
        Coach coach = getCoach(coachUsername);

        if (coach.getCohorts() == null) {
            throw new ResourceAccessException("Cohort does not exist");
        }

        Optional<Cohort> cohortOptional = coach.getCohorts().stream().filter(cohort -> cohort.getId().equals(id)).findFirst();
        if (cohortOptional.isEmpty()) {
            throw new ResourceAccessException("Cohort does not exist");
        }

        Cohort cohort = cohortOptional.get();
        cohort.setName(updateCohortRequest.getName());
        cohort.setDescription(updateCohortRequest.getDescription());

        coachRepository.save(coach);

        return cohort;
    }

    public void delete(String id, String coachUsername) {
        Coach coach = getCoach(coachUsername);

        if (coach.getCohorts() == null) {
            throw new ResourceAccessException("Cohort does not exist");
        }

        Optional<Cohort> cohortOptional = coach.getCohorts().stream().filter(cohort -> cohort.getId().equals(id)).findFirst();
        if (cohortOptional.isEmpty()) {
            throw new ResourceAccessException("Cohort does not exist");
        }

        Cohort cohort = cohortOptional.get();

        coach.removeCohort(cohort);

        coachRepository.save(coach);
    }


    public void addUserToCohort(CreateUserRequest createUserRequest, String id, String coachUsername) {
        Coach coach = getCoach(coachUsername);

        if (coach.getCohorts() == null) {
            throw new ResourceAccessException("Cohort does not exist");
        }

        Optional<Cohort> cohortOptional = coach.getCohorts().stream().filter(cohort -> cohort.getId().equals(id)).findFirst();
        if (cohortOptional.isEmpty()) {
            throw new ResourceAccessException("Cohort does not exist");
        }

        Cohort cohort = cohortOptional.get();

        addChild(createUserRequest, cohort);

        coachRepository.save(coach);
    }

    private static List<CreateUserRequest> parseCsv(MultipartFile file) {

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

    private void addChild(RegisterUserStatus user, Cohort cohort) {
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

        cohort.addChild(child);
    }

    private void addChild(CreateUserRequest user, Cohort cohort) {
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

        cohort.addChild(child);
    }
}
