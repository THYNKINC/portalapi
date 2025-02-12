package com.portal.api.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class WhatsNextMission {
    private Integer mission;
    private String type;
    private long lastCompletedMissionDate;
    private LocalDate dateOfFirstMission;
}
