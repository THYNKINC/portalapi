package com.portal.api.model;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Data;

@Data
public class Child {
	
	private String username;
	private String firstName;
	private String lastName;
	
	private LocalDate dob;
	
	private String headsetId;
	private LocalDate startDate;
	private boolean locked;
}
