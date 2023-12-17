package com.portal.api.model;

import java.time.LocalDate;
import java.util.Date;
import java.util.Map;

import lombok.Data;

@Data
public class Child {
	
	private String username;
	private String firstName;
	private String lastName;
	
	private LocalDate dob;
	
	private Date createdDate;
	private Date updatedDate;
	
	private String headsetId;
	private LocalDate startDate;
	private Map<String, String> labels;
	
	private boolean locked;
}
