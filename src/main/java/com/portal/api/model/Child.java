package com.portal.api.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;
import java.util.Map;

@Data
public class Child {
	
	private String username;
	private String firstName;
	private String lastName;

	private String dob;
	
	private Date createdDate;
	private Date updatedDate;
	
	private String headsetId;
	private LocalDate startDate;
	private Map<String, String> labels;
	
	private boolean locked;

	private String school;

	private String className;

	private String gender;

	private String grade;

	private String diagnosis;

	private String provider;

	private String group;
}
