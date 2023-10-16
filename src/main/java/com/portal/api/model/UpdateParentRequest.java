package com.portal.api.model;

import lombok.Data;

@Data
public class UpdateParentRequest {

	private String firstName;
	private String lastName;
	private String address;
	private String city;
	private String zipCode;
	private String country;
}
