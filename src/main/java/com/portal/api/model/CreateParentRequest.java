package com.portal.api.model;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Data;

@Data
public class CreateParentRequest {

	@NotNull(message = "Password must not be null")
    @Size(min = 8, max = 20, message = "Password length should be between 8 and 20")
    @ValidPassword
    private String password;

    @NotNull(message = "Email must not be null")
    @Email(message = "Invalid email address")
    private String email;

    @NotNull(message = "First name must not be null")
    @Size(min = 1, max = 50, message = "First name length should be between 1 and 50")
    private String firstName;

    @NotNull(message = "Last name must not be null")
    @Size(min = 1, max = 50, message = "Last name length should be between 1 and 50")
    private String lastName;
    
}