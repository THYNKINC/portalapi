package com.portal.api.dto.response;

import lombok.Data;

@Data
public class RegisterUserStatus {

    private String status;

    private String username;

    private String password;

    private String email;

    private String firstName;

    private String lastName;

    private String dob;

    private String school;

    private String className;

    private String gender;

    private String grade;

}
