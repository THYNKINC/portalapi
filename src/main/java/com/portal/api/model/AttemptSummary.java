package com.portal.api.model;

import lombok.Data;

@Data
public class AttemptSummary {

	String username;
	String firstName;
	String lastName;
	String date;
	String mission;
	String type;
	boolean pass;
}
