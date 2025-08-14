package com.portal.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@SuperBuilder
@Data
@NoArgsConstructor
@Document(collection = "cohorts")
public class Cohort {

    @Id
    private String id;

    private String name;

    private String description;

    private String coachUsername;

    private String playerType;

    private Date curriculumEndDate;
}
