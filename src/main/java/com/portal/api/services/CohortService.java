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

    private static final int SCALE = 2;

    private final GameApiService gameApiService;

    private final DelegateRepository delegateRepository;

    private final ImportJobRepository importJobRepository;

    private final CohortsRepository cohortsRepository;

    private final MongoTemplate mongoTemplate;

    private final AnalyticsService analyticsService;

    public CohortService(GameApiService gameApiService, DelegateRepository delegateRepository, ImportJobRepository importJobRepository, CohortsRepository cohortsRepository, MongoTemplate mongoTemplate, AnalyticsService analyticsService) {
        this.gameApiService = gameApiService;
        this.delegateRepository = delegateRepository;
        this.importJobRepository = importJobRepository;
        this.cohortsRepository = cohortsRepository;
        this.mongoTemplate = mongoTemplate;
        this.analyticsService = analyticsService;
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
        child.setDiagnosis(user.getDiagnosis());
        child.setProvider(user.getProvider());
        child.setGroup(user.getGroup());
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
        child.setDiagnosis(user.getDiagnosis());
        child.setProvider(user.getProvider());
        child.setGroup(user.getGroup());
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

    public CohortDetailsResponse getCohortDetails(List<Child> children) {
        double avgNoOfMissionsCompleted = 0;
        double avgWeeksInTraining = 0.0;
        int totalMissionsCompleted = 0;
        double totalWeeksInTraining = 0.0;
        int childrenCount = children.size();
        LocalDate earliestPlayDate = null;
        int mostRecentTrainingSessionDaysAgo = Integer.MAX_VALUE;
        List<CohortChildSummary> cohortChildSummaries = new ArrayList<>();
        List<DaysSinceLastPlayedPerUser> daysSinceLastPlayedPerUser = new ArrayList<>();
        TotalUsers totalUsers = new TotalUsers(0, 0, 0, 0);

        Map<Integer, MissionCompletedPerUser> missionCompletionCount = new HashMap<>();
        for (int i = 1; i <= 16; i++) {
            missionCompletionCount.put(i, new MissionCompletedPerUser(i));
        }

        for (Child child : children) {
            try {

                if (child.isDropped()) {
                    totalUsers.setDropped(totalUsers.getDropped() + 1);
                    continue;
                }

                SearchResponse response = analyticsService.historicalProgress(child.getUsername());
                SearchResponse lastAttemptResponse = analyticsService.lastAttempt(child.getUsername());
                SessionSummary lastSession = SearchResultsMapper.getSession(lastAttemptResponse.getHits().getHits()[0]);
                HistoricalProgressReport progressReport = HistoricalProgressReport.parse(response);
                WhatsNextMission whatsNextMission = getWhatsNext(child);

                daysSinceLastPlayedPerUser.add(new DaysSinceLastPlayedPerUser(child.getUsername(), lastSession.getStartDate()));
                setSummary(child, progressReport, cohortChildSummaries, whatsNextMission, lastSession.getStartDate(), whatsNextMission.getLastCompletedMissionDate());

                MissionCompletedPerUser missionCompletedPerUser = missionCompletionCount.get(whatsNextMission.getMission());

                if (whatsNextMission.getType().equals("completed")) {
                    totalUsers.setCompleted(totalUsers.getCompleted() + 1);
                    missionCompletedPerUser.setCompleted(missionCompletedPerUser.getCompleted() + 1);
                }
                else if (whatsNextMission.getType().equals("runner")) {
                    totalUsers.setActivelyPlaying(totalUsers.getActivelyPlaying() + 1);
                    missionCompletedPerUser.setRunner(missionCompletedPerUser.getRunner() + 1);
                } else {
                    totalUsers.setActivelyPlaying(totalUsers.getActivelyPlaying() + 1);
                    missionCompletedPerUser.setTransference(missionCompletedPerUser.getTransference() + 1);
                }

                int missionsCompleted = progressReport.getMissionsCompleted();
                totalMissionsCompleted += missionsCompleted;
                double weeksInTraining = Math.max(progressReport.getDaysSinceStart() / 7.0, 1.0);
                totalWeeksInTraining += weeksInTraining;
                LocalDate firstPlayDate = progressReport.getFirstPlayed();
                if (earliestPlayDate == null || (firstPlayDate != null && firstPlayDate.isBefore(earliestPlayDate))) {
                    earliestPlayDate = firstPlayDate;
                }

                if (progressReport.getDaysSinceLastAttempt() < mostRecentTrainingSessionDaysAgo) {
                    mostRecentTrainingSessionDaysAgo = progressReport.getDaysSinceLastAttempt();
                }

            } catch (Exception e) {
                // Handle the exception or log it
            }
        }

        if (childrenCount > 0) {
            BigDecimal averageMissionsCompleted = BigDecimal.valueOf(totalMissionsCompleted / (double) childrenCount).setScale(SCALE, RoundingMode.HALF_UP);
            avgNoOfMissionsCompleted = averageMissionsCompleted.doubleValue();
            BigDecimal averageWeeksInTraining = BigDecimal.valueOf(totalWeeksInTraining / (double) childrenCount).setScale(SCALE, RoundingMode.HALF_UP);
            avgWeeksInTraining = averageWeeksInTraining.doubleValue();
        }


        return CohortDetailsResponse.builder()
                .avgNoOfMissionsCompleted(avgNoOfMissionsCompleted)
                .gameplayStartDate(earliestPlayDate)
                .totalUsers(totalUsers)
                .lastGameplaySession(mostRecentTrainingSessionDaysAgo)
                .daysSinceLastPlayedPerUser(daysSinceLastPlayedPerUser)
                .avgNoOfWeeks(avgWeeksInTraining)
                .children(cohortChildSummaries)
                .missionsCompletedPerUser(missionCompletionCount.values().stream().toList())
                .build();
    }

    private WhatsNextMission getWhatsNext(Child child) throws Exception {

        SearchResponse perfReport = analyticsService.sessions(child.getUsername(), PageRequest.of(0, 20));
        List<SessionSummary> perfReportSessions = new ArrayList<>();

        for (SearchHit hit : perfReport.getHits().getHits()) {

            perfReportSessions.add(SearchResultsMapper.getSession(hit));
        }

        WhatsNextMission whatsNextMission = new WhatsNextMission();
        whatsNextMission.setLastCompletedMissionDate(perfReportSessions.get(0).getStartDate());
        List<SessionSummary> mostRecentRunnerMission = mostRecentMissionsByType(perfReportSessions, child.getUsername(), "runner");
        List<SessionSummary> mostRecentTransferenceMission = mostRecentMissionsByType(perfReportSessions, child.getUsername(), "transference");

        if (mostRecentRunnerMission.size() >= 3 || mostRecentRunnerMission.stream().anyMatch(mission -> "PASS".equals(mission.getStatus()))) {
            if (mostRecentTransferenceMission.stream().anyMatch(mission -> "PASS".equals(mission.getStatus()))) {
                if (mostRecentRunnerMission.get(0).getMissionId() == 15) {
                    whatsNextMission.setMission(16);
                    whatsNextMission.setType("completed");
                } else {
                    whatsNextMission.setMission(mostRecentRunnerMission.get(0).getMissionId() + 1);
                    whatsNextMission.setType("runner");
                }
            } else {
                whatsNextMission.setMission(mostRecentRunnerMission.get(0).getMissionId());
                whatsNextMission.setType("transference");
            }
        } else {
            if (mostRecentRunnerMission.stream().anyMatch(mission -> "FAIL".equals(mission.getStatus()))) {
                whatsNextMission.setMission(mostRecentRunnerMission.get(0).getMissionId());
                whatsNextMission.setType("runner");
            } else if (mostRecentRunnerMission.stream().anyMatch(mission -> "PASS".equals(mission.getStatus())) && mostRecentTransferenceMission == null) {
                whatsNextMission.setMission(mostRecentRunnerMission.get(0).getMissionId());
                whatsNextMission.setType("runner");
            }
        }

        return whatsNextMission;
    }

    private void setSummary(Child child, HistoricalProgressReport progressReport, List<CohortChildSummary> cohortChildSummaries, WhatsNextMission whatsNextMission, long daysSinceLastPlayed, long lastCompletedMissionDate) {
        CohortChildSummary cohortChildSummary = new CohortChildSummary(child);

        String nexMission = whatsNextMission.getMission() == 16
                ? "Completed"
                : whatsNextMission.getMission().toString().concat(" ").concat(whatsNextMission.getType().equals("runner") ? "Runner" : "Fog Analysis");

        cohortChildSummary.setDaysSinceLastPlayed(daysSinceLastPlayed);
        cohortChildSummary.setActive(!child.isDropped());
        cohortChildSummary.setNextMission(nexMission);
        cohortChildSummary.setLastCompletedMission(lastCompletedMissionDate);
        cohortChildSummaries.add(cohortChildSummary);
    }

    public List<Cohort> getAllCohorts() {
        return cohortsRepository.findAll();
    }


    private List<SessionSummary> mostRecentMissionsByType(List<SessionSummary> perfReport, String username, String type) throws Exception {
        RecentMissionResponse recentMissionResponse = recentMission(username);

        int missionsCompleted = recentMissionResponse.getMissionNumber();

        return perfReport.stream()
                .filter(session -> session.getMissionId() == missionsCompleted && session.getType().equals(type))
                .collect(Collectors.toList());
    }

    private RecentMissionResponse recentMission(String username) throws Exception {
        SearchResponse searchResponse = analyticsService.lastAttempt(username);

        if (searchResponse.getHits().getHits().length == 0)
            return new RecentMissionResponse();

        SessionSummary session = SearchResultsMapper.getSession(searchResponse.getHits().getHits()[0]);

        RecentMissionResponse recentMissionResponse = new RecentMissionResponse();

        recentMissionResponse.setMissionNumber(session.getMissionId());
        recentMissionResponse.setSessionId(session.getId());

        if (session instanceof RunnerSummary) {

            RunnerSummary runner = (RunnerSummary) session;

            recentMissionResponse.setMissionStatus(runner.getStatus());
            recentMissionResponse.setMissionRating(runner.getStars().size());
            recentMissionResponse.setType("runner");
        } else if (session instanceof TransferenceSummary) {

            TransferenceSummary xfer = (TransferenceSummary) session;

            recentMissionResponse.setMissionStatus(session.getStatus());
            recentMissionResponse.setMissionRating(xfer.getDecoded() * 100 / xfer.getTarget());
            recentMissionResponse.setType("transference");
        } else {

            recentMissionResponse.setMissionStatus("PASS");
            recentMissionResponse.setMissionRating(100);
            recentMissionResponse.setType("vigilock");
        }

        return recentMissionResponse;
    }
}
