package com.portal.api.services;

import com.portal.api.dto.WhatsNextMission;
import com.portal.api.dto.response.*;
import com.portal.api.model.*;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.metrics.TopHits;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CohortDetailsService {

    private static final int SCALE = 2;

    private final AnalyticsService analyticsService;

    public CohortDetailsService(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
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
                WhatsNextMission whatsNextMission = getWhatsNext(child, progressReport.getHighestMission());

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

    private WhatsNextMission getWhatsNext(Child child, int highestMission) throws Exception {

        List<SessionSummary> perfReportSessions = new ArrayList<>();
        if (highestMission == 15) {
            SearchResponse response = analyticsService.latestSessionsPerMission(child.getUsername(), String.valueOf(highestMission));
            Terms types = response.getAggregations().get("types");
            List<SessionSummary> sessionSummaries = new ArrayList<>();

            for (Terms.Bucket bucket : types.getBuckets()) {

                TopHits latest = bucket.getAggregations().get("latest");

                if (latest.getHits().getHits().length > 0)
                    sessionSummaries.add(SearchResultsMapper.getSession(latest.getHits().getAt(0)));
            }

            perfReportSessions = sessionSummaries;
        } else {
            SearchResponse perfReport = analyticsService.sessions(child.getUsername(), PageRequest.of(0, 20));

            for (SearchHit hit : perfReport.getHits().getHits()) {

                perfReportSessions.add(SearchResultsMapper.getSession(hit));
            }
        }

        WhatsNextMission whatsNextMission = new WhatsNextMission();
        whatsNextMission.setLastCompletedMissionDate(perfReportSessions.get(0).getStartDate());

        List<SessionSummary> mostRecentRunnerMission = mostRecentMissionsByType(perfReportSessions, "runner", highestMission);
        List<SessionSummary> mostRecentTransferenceMission = mostRecentMissionsByType(perfReportSessions, "transference", highestMission);

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

    private List<SessionSummary> mostRecentMissionsByType(List<SessionSummary> perfReport, String type, int highestMission) throws Exception {
        return perfReport.stream()
                .filter(session -> session.getMissionId() == highestMission && session.getType().equals(type))
                .collect(Collectors.toList());
    }
}
