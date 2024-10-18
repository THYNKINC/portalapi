package com.portal.api.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CohortChildSummary {
    private String name;
    private String username;
    private LocalDate lastCompletedMission;
    private int nextMission;
}
