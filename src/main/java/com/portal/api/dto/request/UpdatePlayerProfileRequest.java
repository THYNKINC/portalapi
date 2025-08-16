package com.portal.api.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdatePlayerProfileRequest {
    private boolean dropped;
    private LocalDate curriculumEndDate;
}
