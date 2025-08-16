package com.portal.api.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class CreateCohortRequest {

    @NotNull(message = "Name must not be null")
    private String name;

    private String description;

    private String playerType;

    private LocalDate curriculumEndDate;

}
