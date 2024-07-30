package com.portal.api.dto.request;

import java.time.LocalDate;
import java.util.Map;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class UpdateChildRequest {

    @NotNull(message = "First name must not be null")
    @Size(min = 1, max = 50, message = "First name length should be between 1 and 50")
    private String firstName;

    @NotNull(message = "Last name must not be null")
    @Size(min = 1, max = 50, message = "Last name length should be between 1 and 50")
    private String lastName;

    @NotNull(message = "Date of birth must not be null")
    private String dob;
    
    private Map<String, String> labels;
}