package com.portal.api.services;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.portal.api.dto.WhatsNextMission;
import com.portal.api.dto.request.CreateCohortRequest;
import com.portal.api.dto.request.CreateCohortUserRequest;
import com.portal.api.dto.response.*;
import com.portal.api.exception.ResourceNotFoundException;
import com.portal.api.model.*;
import com.portal.api.repositories.CohortsRepository;
import com.portal.api.repositories.DelegateRepository;
import com.portal.api.repositories.ImportJobRepository;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CohortService {

    private final GameApiService gameApiService;

    private final DelegateRepository delegateRepository;

    private final ImportJobRepository importJobRepository;

    private final CohortsRepository cohortsRepository;

    private final MongoTemplate mongoTemplate;

    private final ImportWorkerService importWorkerService;

    public CohortService(GameApiService gameApiService, DelegateRepository delegateRepository, ImportJobRepository importJobRepository, CohortsRepository cohortsRepository, MongoTemplate mongoTemplate, ImportWorkerService importWorkerService) {
        this.gameApiService = gameApiService;
        this.delegateRepository = delegateRepository;
        this.importJobRepository = importJobRepository;
        this.cohortsRepository = cohortsRepository;
        this.mongoTemplate = mongoTemplate;
        this.importWorkerService = importWorkerService;
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
        job.setCreatedDate(LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
        job.setStatus(ImportStatus.RUNNING);

        importJobRepository.save(job);

        importWorkerService.processUsersAsync(file, cohort, coach, bearerToken, job.getJobId());
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

    private Child addToCohort(CreateCohortUserRequest user, Cohort cohort, String adminJwt) {

        gameApiService.createNewUserForCohort(user, adminJwt);

        Child child = new Child();
        child.setFirstName(user.getFirstName());
        child.setLastName(user.getLastName());
        child.setUsername(user.getUsername());
        child.setDob(user.getDob());
        child.setSchool(user.getSchool());
        child.setClassName(user.getClassName());
        child.setGender(user.getGender());
        child.setGrade(user.getGrade());
        child.setCreatedDate(new Date());
        child.setDiagnosis(user.getDiagnosis());
        child.setProvider(user.getProvider());
        child.setGroup(user.getGroup());
        child.setLocked(false);
        child.setDropped(false);
        child.setStartDate(null);
        child.setUpdatedDate(null);
        child.setHeadsetId(null);
        child.setHeadsetType(null);
        child.setDroppedTime(null);
        child.setParentFirstName(user.getParentFirstName());
        child.setParentLastName(user.getParentLastName());
        child.setEmail(user.getEmail());
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

    public List<Cohort> getAllCohorts() {
        return cohortsRepository.findAll();
    }
}
