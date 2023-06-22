package com.portal.api.model;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class CreateUserRequest {
	
	private String username;

    @NotNull(message = "Password must not be null")
    private String password;

    @NotNull(message = "Email must not be null")
    private String email;

    @NotNull(message = "First name must not be null")
    private String firstName;

    @NotNull(message = "Last name must not be null")
    private String lastName;

    @NotNull(message = "Parent must not be null")
    private String parent;
    
}