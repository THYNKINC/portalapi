package com.portal.api.services;

import com.portal.api.dto.WhatsNextMission;
import com.portal.api.dto.response.*;
import com.portal.api.model.*;
import org.opensearch.action.search.SearchResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CohortDetailsService {

    private static final int SCALE = 2;

    private final AnalyticsService analyticsService;
    private final WhatsNextMissionService whatsNextMissionService;
    private final CohortService cohortService;

    public CohortDetailsService(
        AnalyticsService analyticsService,
        WhatsNextMissionService whatsNextMissionService,
        CohortService cohortService
    ) {
        this.analyticsService = analyticsService;
        this.whatsNextMissionService = whatsNextMissionService;
        this.cohortService = cohortService;
    }

    public CohortDetailsResponse getCohortDetails(String cohortId) {
        Cohort cohort = cohortService.getCohort(cohortId);
        if (cohort == null) {
            throw new NoSuchElementException();
        }

        List<Child> children = cohortService.getChildrenFromCohort(cohort.getId());
        if (children == null) {
            throw new NoSuchElementException();
        }

        double avgNoOfMissionsCompleted = 0;
        double avgWeeksInTraining = 0.0;
        int totalMissionsCompleted = 0;
        double totalWeeksInTraining;
        int childrenCount = children.size();
        LocalDate earliestPlayDate = null;
        int mostRecentTrainingSessionDaysAgo = Integer.MAX_VALUE;
        List<CohortChildSummary> cohortChildSummaries = new ArrayList<>();
        List<DaysSinceLastPlayedPerUser> daysSinceLastPlayedPerUser = new ArrayList<>();
        TotalUsers totalUsers = new TotalUsers(childrenCount, 0, 0, 0, 0);

        Map<Integer, MissionCompletedPerUser> missionCompletionCount = initializeMissionCompletedMap();

        for (Child child : children) {
            try {

                if (child.isDropped()) {
                    handleDroppedChild(child, cohortChildSummaries, totalUsers);
                    continue;
                }

                SearchResponse response = analyticsService.historicalProgress(child.getUsername());
                SearchResponse lastAttemptResponse = analyticsService.lastAttempt(child.getUsername());
                SessionSummary lastSession = SearchResultsMapper.getSession(lastAttemptResponse.getHits().getHits()[0]);
                HistoricalProgressReport progressReport = HistoricalProgressReport.parse(response);
                WhatsNextMission whatsNextMission = whatsNextMissionService.getWhatsNext(child, progressReport.getHighestMission());

                setSummary(child, cohortChildSummaries, whatsNextMission, lastSession.getStartDate(), whatsNextMission.getLastCompletedMissionDate());

                MissionCompletedPerUser missionCompletedPerUser = missionCompletionCount.get(whatsNextMission.getMission());

                if (!whatsNextMission.getType().equals("completed")) {
                    daysSinceLastPlayedPerUser.add(new DaysSinceLastPlayedPerUser(child.getUsername(), lastSession.getStartDate()));
                }

                if (whatsNextMission.getType().equals("completed") || childCurriculumCompleted(child) || cohortCurriculumCompleted(cohort)) {
                    totalUsers.setCompleted(totalUsers.getCompleted() + 1);
                    missionCompletedPerUser.setCompleted(missionCompletedPerUser.getCompleted() + 1);
                } else if (whatsNextMission.getType().equals("runner")) {
                    totalUsers.setActivelyPlaying(totalUsers.getActivelyPlaying() + 1);
                    missionCompletedPerUser.setRunner(missionCompletedPerUser.getRunner() + 1);
                } else {
                    totalUsers.setActivelyPlaying(totalUsers.getActivelyPlaying() + 1);
                    missionCompletedPerUser.setTransference(missionCompletedPerUser.getTransference() + 1);
                }

                totalMissionsCompleted = getTotalMissionsCompleted(totalMissionsCompleted, progressReport);

                LocalDate firstPlayDate = progressReport.getFirstPlayed();
                if (earliestPlayDate == null || (firstPlayDate != null && firstPlayDate.isBefore(earliestPlayDate))) {
                    earliestPlayDate = firstPlayDate;
                }

                if (progressReport.getDaysSinceLastAttempt() < mostRecentTrainingSessionDaysAgo) {
                    mostRecentTrainingSessionDaysAgo = progressReport.getDaysSinceLastAttempt();
                }

            } catch (Exception e) {
                handleChildError(child, cohortChildSummaries, totalUsers);
            }
        }

        totalWeeksInTraining = getTotalWeeksInTraining(cohort, earliestPlayDate);

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
                .totalWeeksInTraining(totalWeeksInTraining)
                .curriculumEndDate(cohort.getCurriculumEndDate())
                .cohortType(cohort.getPlayerType())
                .build();
    }

    private Map<Integer, MissionCompletedPerUser> initializeMissionCompletedMap() {
        Map<Integer, MissionCompletedPerUser> missionCompletionCount = new HashMap<>();
        for (int i = 1; i <= 16; i++) {
            missionCompletionCount.put(i, new MissionCompletedPerUser(i));
        }
        return missionCompletionCount;
    }

    private void handleDroppedChild(Child child, List<CohortChildSummary> cohortChildSummaries, TotalUsers totalUsers) {
        CohortChildSummary cohortChildSummary = new CohortChildSummary(child);
        cohortChildSummary.setDaysSinceLastPlayed(0L);
        cohortChildSummary.setActive(!child.isDropped());
        cohortChildSummary.setNextMission(null);
        cohortChildSummary.setLastCompletedMission(0L);
        cohortChildSummaries.add(cohortChildSummary);
        totalUsers.setDropped(totalUsers.getDropped() + 1);
    }

    private void handleChildError(Child child, List<CohortChildSummary> cohortChildSummaries, TotalUsers totalUsers) {
        CohortChildSummary cohortChildSummary = new CohortChildSummary(child);
        cohortChildSummary.setDaysSinceLastPlayed(0L);
        cohortChildSummary.setActive(!child.isDropped());
        cohortChildSummary.setNextMission(null);
        cohortChildSummary.setLastCompletedMission(0L);
        cohortChildSummaries.add(cohortChildSummary);
        totalUsers.setNotStarted(totalUsers.getNotStarted() + 1);
    }

    private int getTotalMissionsCompleted(int totalMissionsCompleted, HistoricalProgressReport progressReport) {
        totalMissionsCompleted += progressReport.getHighestMission();
        return totalMissionsCompleted;
    }

    private long getTotalWeeksInTraining(Cohort cohort, LocalDate gamePlayStartDate) {
        if (gamePlayStartDate == null) return 0L;

        LocalDate curriculumEndDate = cohort.getCurriculumEndDate() != null ? cohort.getCurriculumEndDate() : LocalDate.now();
        return ChronoUnit.WEEKS.between(gamePlayStartDate, curriculumEndDate);
    }

    private void setSummary(Child child, List<CohortChildSummary> cohortChildSummaries, WhatsNextMission whatsNextMission, long daysSinceLastPlayed, long lastCompletedMissionDate) {
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

    private boolean childCurriculumCompleted(Child child) {
        return child.getCurriculumEndDate() != null && isOnOrBeforeToday(child.getCurriculumEndDate());
    }

    private boolean cohortCurriculumCompleted(Cohort cohort) {
        return cohort.getCurriculumEndDate() != null && isOnOrBeforeToday(cohort.getCurriculumEndDate());
    }

    private boolean isOnOrBeforeToday(LocalDate date) {
        LocalDate now = LocalDate.now();
        return date != null && (date.isBefore(now) || date.isEqual(now));
    }

}
