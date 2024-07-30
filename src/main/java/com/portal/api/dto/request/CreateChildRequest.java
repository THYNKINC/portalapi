package com.portal.api.dto.request;

import java.time.LocalDate;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.portal.api.model.ValidPassword;
import org.springframework.format.annotation.DateTimeFormat;

import lombok.Data;

@Data
public class CreateChildRequest {
	
	@NotNull(message = "Username must not be null")
	@Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Username should contain only letters and numbers")
    @Size(min = 3, max = 15, message = "Username length should be between 3 and 15")
    private String username;

    @NotNull(message = "Password must not be null")
    @Size(min = 8, max = 20, message = "Password length should be between 8 and 20")
    @ValidPassword
    private String password;

    @NotNull(message = "First name must not be null")
    @Size(min = 1, max = 50, message = "First name length should be between 1 and 50")
    private String firstName;

    @NotNull(message = "Last name must not be null")
    @Size(min = 1, max = 50, message = "Last name length should be between 1 and 50")
    private String lastName;

    @NotNull(message = "Date of birth must not be null")
    private String dob;
    
    private String headsetId;
}