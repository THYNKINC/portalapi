package com.portal.api.model;

import lombok.Data;

@Data
public class Attempt {

	String username;
	String firstName;
	String lastName;
	String date;
	String mission;
	String type;
	boolean pass;
}
