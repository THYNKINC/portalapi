package com.portal.api.services;

import com.portal.api.dto.WhatsNextMission;
import com.portal.api.model.Child;
import com.portal.api.model.SessionSummary;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.metrics.TopHits;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WhatsNextMissionService {

    private final AnalyticsService analyticsService;

    public WhatsNextMissionService(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    public WhatsNextMission getWhatsNext(Child child, int highestMission) throws Exception {

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
        List<SessionSummary> mostRecentPvtMission = mostRecentMissionsByType(perfReportSessions, "pvt", 1);

        if (highestMission == 0){
            if (mostRecentPvtMission.stream().anyMatch(mission -> "PASS".equals(mission.getStatus()))) {
                whatsNextMission.setMission(2);
                whatsNextMission.setType("runner");
                return whatsNextMission;
            } else {
                whatsNextMission.setMission(1);
                whatsNextMission.setType("transference");
                return whatsNextMission;
            }
        }

        if (highestMission == 15) {
            if (mostRecentTransferenceMission.stream().anyMatch(mission -> "PASS".equals(mission.getStatus())) || mostRecentTransferenceMission.stream().anyMatch(SessionSummary::isCompleted)) {
                whatsNextMission.setMission(16);
                whatsNextMission.setType("completed");
                return whatsNextMission;
            }
        }

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

    private List<SessionSummary> mostRecentMissionsByType(List<SessionSummary> perfReport, String type, int highestMission) throws Exception {
        return perfReport.stream()
                .filter(session -> session.getMissionId() == highestMission && session.getType().equals(type))
                .collect(Collectors.toList());
    }
}
