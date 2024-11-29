package com.portal.api.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CohortDetailsResponse {

    public CohortDetailsResponse(TotalUsers totalUsers) {
        this.totalUsers = totalUsers;
    }

    private TotalUsers totalUsers;

    private double avgNoOfWeeks;

    private LocalDate gameplayStartDate;

    private int lastGameplaySession;

    private double avgNoOfMissionsCompleted;

    private List<CohortChildSummary> children = new ArrayList<>();

    private List<MissionCompletedPerUser> missionsCompletedPerUser = new ArrayList<>();
    private List<DaysSinceLastPlayedPerUser> daysSinceLastPlayedPerUser = new ArrayList<>();

    private String cohortType;
}
