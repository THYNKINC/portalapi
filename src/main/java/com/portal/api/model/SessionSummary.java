package com.portal.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class SessionSummary {

	@JsonIgnore
	String _id;
	
	String id;
	String type;
	String userId;
	String firstName;
	String lastName;
	String parentFirstName;
	String parentLastName;
	String parentEmail;
	int missionId;
	long startDate;
	long endDate;
	long createdDate;
	int duration;
	String status;
	int bciAvg;
	int bciStdDeviation;
	boolean completed;
}
