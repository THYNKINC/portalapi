package com.portal.api.services;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.portal.api.dto.request.CreateCohortRequest;
import com.portal.api.dto.request.CreateCohortUserRequest;
import com.portal.api.dto.response.ImportStatus;
import com.portal.api.dto.response.RegisterUserStatus;
import com.portal.api.exception.ResourceNotFoundException;
import com.portal.api.model.Child;
import com.portal.api.model.Cohort;
import com.portal.api.model.Delegate;
import com.portal.api.model.ImportJob;
import com.portal.api.repositories.CohortsRepository;
import com.portal.api.repositories.DelegateRepository;
import com.portal.api.repositories.ImportJobRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.*;

@Service
public class CohortService {

    private final GameApiService gameApiService;

    private final DelegateRepository delegateRepository;

    private final ImportJobRepository importJobRepository;

    private final CohortsRepository cohortsRepository;

    private final MongoTemplate mongoTemplate;


    public CohortService(GameApiService gameApiService, DelegateRepository delegateRepository, ImportJobRepository importJobRepository, CohortsRepository cohortsRepository, MongoTemplate mongoTemplate) {
        this.gameApiService = gameApiService;
        this.delegateRepository = delegateRepository;
        this.importJobRepository = importJobRepository;
        this.cohortsRepository = cohortsRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public void processUsersCsv(MultipartFile file, String coachUsername, String cohortId, String bearerToken) {

        Optional<Delegate> coachOptional = delegateRepository.findById(coachUsername);
        if (coachOptional.isEmpty()) {
            throw new ResourceNotFoundException("Coach not found");
        }

        Optional<Cohort> cohortOptional = cohortsRepository.findById(cohortId);
        if (cohortOptional.isEmpty()) {
            throw new ResourceNotFoundException("Cohort not found");
        }

        Delegate coach = coachOptional.get();
        Cohort cohort = cohortOptional.get();

        ImportJob job = new ImportJob();
        job.setCohortId(cohortId);
        job.setCoachUsername(coachUsername);
        job.setCoachFullName(coach.getFirstName().concat(" ").concat(coach.getLastName()));
        job.setCohortName(cohort.getName());
        job.setCreatedDate(LocalDate.now());
        job.setStatus(ImportStatus.RUNNING);

        importJobRepository.save(job);

        processUsersAsync(file, cohort, coach, bearerToken, job.getJobId());
    }

    @Async
    public void processUsersAsync(MultipartFile file, Cohort cohort, Delegate coach, String bearerToken, String jobId) {

        ImportJob job = importJobRepository.findById(jobId).orElse(new ImportJob());

        List<CreateCohortUserRequest> users = parseCsv(file);
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

    public List<Cohort> getCohorts(String username) {
        return cohortsRepository.findAllByCoachUsername(username);
    }

    public Cohort createCohort(CreateCohortRequest createCohortRequest, String coachUsername) {

        Cohort cohort = new Cohort();
        cohort.setName(createCohortRequest.getName());
        cohort.setDescription(createCohortRequest.getDescription());
        cohort.setCoachUsername(coachUsername);
        cohort.setPlayerType(createCohortRequest.getPlayerType());

        cohortsRepository.save(cohort);

        return cohort;
    }

    private Delegate getCoach(String coachId) {
        Optional<Delegate> coachOptional = delegateRepository.findById(coachId);
        if (coachOptional.isEmpty()) {
            throw new ResourceNotFoundException("Coach does not exist");
        }
        return coachOptional.get();
    }

    public Cohort update(CreateCohortRequest updateCohortRequest, String id) {
        Optional<Cohort> cohortOptional = cohortsRepository.findById(id);
        if (cohortOptional.isEmpty()) {
            throw new ResourceNotFoundException("Cohort does not exist");
        }

        Cohort cohort = cohortOptional.get();
        cohort.setName(updateCohortRequest.getName());
        cohort.setDescription(updateCohortRequest.getDescription());
        cohort.setPlayerType(updateCohortRequest.getPlayerType());

        cohortsRepository.save(cohort);

        return cohort;
    }

    public void delete(String cohortId) {
        Optional<Cohort> cohortOptional = cohortsRepository.findById(cohortId);
        if (cohortOptional.isEmpty()) {
            throw new ResourceNotFoundException("Cohort does not exist");
        }

        Cohort cohort = cohortOptional.get();
        cohortsRepository.delete(cohort);
    }


    public Child addUserToCohort(CreateCohortUserRequest createUserRequest, String cohortId, String coachUsername, String adminJwt) {
        Delegate coach = getCoach(coachUsername);

        Optional<Cohort> cohortOptional = cohortsRepository.findById(cohortId);
        if (cohortOptional.isEmpty()) {
            throw new ResourceNotFoundException("Cohort does not exist");
        }

        Cohort cohort = cohortOptional.get();

        Child child = addToCohort(createUserRequest, cohort, adminJwt);
        coach.addChild(child);

        delegateRepository.save(coach);

        return child;
    }

    private List<CreateCohortUserRequest> parseCsv(MultipartFile file) {

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream())) {

            CsvToBean<CreateCohortUserRequest> csvToBean = new CsvToBeanBuilder<CreateCohortUserRequest>(reader)
                    .withType(CreateCohortUserRequest.class)
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
        child.setDob(user.getDob());
        child.setSchool(user.getSchool());
        child.setClassName(user.getClassName());
        child.setGender(child.getGender());
        child.setGrade(user.getGrade());
        child.setCreatedDate(new Date());
        child.setLabels(Map.of("cohort", cohort.getId()));

        return child;
    }

    private Child addToCohort(CreateCohortUserRequest user, Cohort cohort, String adminJwt) {

        gameApiService.createNewUserForCohort(user, adminJwt);

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
        child.setLabels(Map.of("cohort", cohort.getId()));

        return child;
    }

    public Cohort getCohort(String cohortId) {
        Optional<Cohort> cohortOptional = cohortsRepository.findById(cohortId);
        if (cohortOptional.isEmpty()) {
            throw new ResourceNotFoundException("Cohort does not exist");
        }

        return cohortOptional.get();
    }

    public List<Child> getChildrenByUsername(List<String> usernames) {
        Criteria usernameCriteria = Criteria.where("username").in(usernames);

        TypedAggregation<Delegate> aggregation = Aggregation.newAggregation(Delegate.class,
                Aggregation.unwind("children"),
                Aggregation.replaceRoot("children"),
                Aggregation.match(usernameCriteria)
        );

        AggregationResults<Child> result = mongoTemplate
                .aggregate(aggregation, Child.class);

        return result.getMappedResults();
    }

    public List<Child> getChildrenFromCohort(String cohortId) {
        Criteria cohortCriteria = Criteria.where("labels.cohort").is(cohortId);

        TypedAggregation<Delegate> aggregation = Aggregation.newAggregation(Delegate.class,
                Aggregation.unwind("children"),
                Aggregation.replaceRoot("children"),
                Aggregation.match(cohortCriteria)
        );

        AggregationResults<Child> result = mongoTemplate
                .aggregate(aggregation, Child.class);

        return result.getMappedResults();
    }
}
