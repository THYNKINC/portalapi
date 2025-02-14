package com.portal.api.services;

import com.portal.api.dto.WhatsNextMission;
import com.portal.api.dto.response.*;
import com.portal.api.model.*;
import org.opensearch.action.search.SearchResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CohortDetailsService {

    private static final int SCALE = 2;

    private final AnalyticsService analyticsService;

    private final WhatsNextMissionService whatsNextMissionService;

    public CohortDetailsService(AnalyticsService analyticsService, WhatsNextMissionService whatsNextMissionService) {
        this.analyticsService = analyticsService;
        this.whatsNextMissionService = whatsNextMissionService;
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

                if (whatsNextMission.getType().equals("completed")) {
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
                totalWeeksInTraining = getTotalWeeksInTraining(progressReport, totalWeeksInTraining);

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

    private double getTotalWeeksInTraining(HistoricalProgressReport progressReport, double totalWeeksInTraining) {
        double weeksInTraining = Math.max(progressReport.getDaysSinceStart() / 7.0, 1.0);
        totalWeeksInTraining += weeksInTraining;
        return totalWeeksInTraining;
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

}
