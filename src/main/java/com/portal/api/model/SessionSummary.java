package com.portal.api.model;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public abstract class SessionSummary {

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
	int duration;
	String status;
	int bciAvg;
	int bciStdDeviation;
	boolean completed;
}
