package com.portal.api.services.impl;

import com.portal.api.dto.request.UpdatePlayerProfileRequest;
import com.portal.api.dto.response.GeneralStatus;
import com.portal.api.dto.response.PlayerStatus;
import com.portal.api.dto.response.Profile;
import com.portal.api.model.Child;
import com.portal.api.model.Cohort;
import com.portal.api.model.HistoricalProgressReport;
import com.portal.api.model.TransferenceSummary;
import com.portal.api.services.*;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

/**
 * Implementation of the PlayerService interface for managing player-related operations.
 */
@Service
public class PlayerServiceImpl implements PlayerService {

    private final ParentService parentService;
    private final CohortService cohortService;
    private final CoachService coachService;
    private final AnalyticsService analyticsService;

    public PlayerServiceImpl(
        ParentService parentService,
        CohortService cohortService,
        CoachService coachService,
        AnalyticsService analyticsService
    ) {
        this.parentService = parentService;
        this.cohortService = cohortService;
        this.coachService = coachService;
        this.analyticsService = analyticsService;
    }

    @Override
    public boolean updatePlayerProfile(String username, UpdatePlayerProfileRequest updatePlayerProfileRequest) {
        boolean isInCohort = false;

        List<Child> children = parentService.getChildrenByUsername(Collections.singletonList(username));
        if (children.size() != 1) {
            children = cohortService.getChildrenByUsername(Collections.singletonList(username));
            if (children.size() != 1) {
                throw new NoSuchElementException("Unable to find child with username " + username);
            } else {
                isInCohort = true;
            }
        }

        Child child = children.get(0);
        child = setPlayerDropStatus(child, updatePlayerProfileRequest.isDropped());

        if (updatePlayerProfileRequest.getCurriculumEndDate() != null) {
            child.setCurriculumEndDate(updatePlayerProfileRequest.getCurriculumEndDate());
        }

        if (isInCohort) {
            coachService.updateChild(children.get(0));
        } else {
            parentService.updateChild(child);
        }

        return true;
    }

    @Override
    public Profile getPlayerProfile(String username) throws Exception {
        List<Child> children = parentService.getChildrenByUsername(Collections.singletonList(username));
        if (children.size() != 1) {
            children = cohortService.getChildrenByUsername(Collections.singletonList(username));
            if (children.size() != 1) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unable to find child with username " + username);
            }
        }

        Child child = children.get(0);

        boolean skipPlayerStatus = false;
        SearchResponse response = analyticsService.historicalProgress(child.getUsername());
        HistoricalProgressReport progressReport = null;
        PlayerStatus playerStatus = new PlayerStatus();
        try {
            progressReport = HistoricalProgressReport.parse(response);
        } catch (ParseException e) {
            playerStatus.setStarted("Not yet started");
            playerStatus.setCompleted("N/A");
            playerStatus.setDropped(child.isDropped() ? "Yes" : "No");
            playerStatus.setGeneralStatus(GeneralStatus.ACTIVE);
            skipPlayerStatus = true;
        }

        if (!skipPlayerStatus) {
            playerStatus.setStarted(progressReport.getStartDate().toString());
            playerStatus.setDropped(child.isDropped() ? "Yes" : "No");
            playerStatus.setCompleted("N/A");
            playerStatus.setGeneralStatus(GeneralStatus.ACTIVE);

            List<TransferenceSummary> mappedSessions = new ArrayList<>();
            if (progressReport.getHighestMission() == 15) {
                SearchResponse sessions = analyticsService.sessions(child.getUsername(), String.valueOf(progressReport.getHighestMission()), "transference");
                for (SearchHit hit : sessions.getHits().getHits()) {
                    mappedSessions.add((TransferenceSummary) SearchResultsMapper.getSession(hit));
                }

                Optional<TransferenceSummary> passTransference = mappedSessions.stream().filter(session -> session.isCompleted() && session.getStatus().equals("PASS")).findFirst();
                passTransference.ifPresent(transferenceSummary -> {
                    if (transferenceSummary.getEndDate() > 0) {
                        playerStatus.setCompleted(LocalDate.ofEpochDay(transferenceSummary.getEndDate() / 86_400_000L).toString());
                        playerStatus.setGeneralStatus(GeneralStatus.COMPLETED);
                    }
                });
            }
        }

        Profile profile = new Profile(child, playerStatus);
        String cohortId = child.getLabels().get("cohort");
        Cohort cohort = cohortService.getCohort(cohortId);
        if (cohort != null) {
            profile.setCohortType(cohort.getPlayerType());
            profile.setCohortName(cohort.getName());
        }

        return profile;
    }

    @Override
    public Child setPlayerDropStatus(Child child, boolean dropped) {
        child.setDropped(dropped);

        if (dropped) {
            if (child.getDroppedTime() == null || child.getDroppedTime().isEmpty()) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
                child.setDroppedTime(dateFormat.format(new Date()));
            }
        } else {
            child.setDroppedTime(null);
        }

        return child;
    }
}
