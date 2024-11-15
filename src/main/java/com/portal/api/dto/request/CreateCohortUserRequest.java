package com.portal.api.dto.request;

import com.opencsv.bean.CsvBindByName;
import com.portal.api.model.ValidPassword;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class CreateCohortUserRequest {

    @CsvBindByName(column = "username")
	private String username;

    @CsvBindByName(column = "password")
	@NotNull(message = "Password must not be null")
    @Size(min = 8, max = 20, message = "Password length should be between 8 and 20")
    @ValidPassword
    private String password;

    @CsvBindByName(column = "email")
    @NotNull(message = "Email must not be null")
    @Email(message = "Invalid email address")
    private String email;

    @CsvBindByName(column = "firstName")
    @NotNull(message = "First name must not be null")
    @Size(min = 1, max = 50, message = "First name length should be between 1 and 50")
    private String firstName;

    @CsvBindByName(column = "lastName")
    @NotNull(message = "Last name must not be null")
    @Size(min = 1, max = 50, message = "Last name length should be between 1 and 50")
    private String lastName;

    @CsvBindByName(column = "dob")
    private String dob;

    @CsvBindByName(column = "school")
    private String school;

    @CsvBindByName(column = "className")
    private String className;

    @CsvBindByName(column = "gender")
    private String gender;

    @CsvBindByName(column = "grade")
    private String grade;

    @CsvBindByName(column = "diagnosis")
    private String diagnosis;

    @CsvBindByName(column = "provider")
    private String provider;

    @CsvBindByName(column = "group")
    private String group;
}