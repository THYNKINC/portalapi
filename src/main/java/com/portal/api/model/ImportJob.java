package com.portal.api.model;

import com.portal.api.dto.response.RegisterUserStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;

@Data
@Document(collection = "importJob")
public class ImportJob {

    @Id
    private String jobId;

    private String status;

    private String cohortId;

    private String cohortName;

    private String coachUsername;

    private String coachFullName;

    private List<RegisterUserStatus> users;

    private String error;

    private String createdDate;

    private String updatedDate;
}
