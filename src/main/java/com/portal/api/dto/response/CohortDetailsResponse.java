package com.portal.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CohortDetailsResponse {

    public CohortDetailsResponse(int size) {
        this.totalUsers = size;
    }

    private int totalUsers;

    private int avgNoOfWeeks;

    private LocalDate gameplayStartDate;

    private int lastGameplaySession;

    private int avgNoOfMissionsCompleted;
}
