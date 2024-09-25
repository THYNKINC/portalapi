package com.portal.api.dto.response;

import lombok.Data;

@Data
public class ImportStatus {

    public static final String REGISTERED = "registered";
    public static final String FAILED = "failed";
    public static final String COMPLETED = "completed";
    public static final String COMPLETED_WITH_ERRORS = "completed-with-errors";
    public static final String RUNNING = "running";

    private String status;

    private String error;
}
