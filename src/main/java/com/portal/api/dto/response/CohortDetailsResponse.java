package com.portal.api.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CohortDetailsResponse {

    public CohortDetailsResponse(int size) {
        this.totalUsers = size;
    }

    private int totalUsers;

    private double avgNoOfWeeks;

    private LocalDate gameplayStartDate;

    private int lastGameplaySession;

    private double avgNoOfMissionsCompleted;
}
