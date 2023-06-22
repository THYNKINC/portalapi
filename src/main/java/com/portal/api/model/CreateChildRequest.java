package com.portal.api.model;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class CreateChildRequest {
	
	@NotNull(message = "Username must not be null")
    private String username;

    @NotNull(message = "Password must not be null")
    private String password;

    @NotNull(message = "First name must not be null")
    private String firstName;

    @NotNull(message = "Last name must not be null")
    private String lastName;
    
}