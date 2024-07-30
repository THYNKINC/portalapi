package com.portal.api.model;

import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public class PortalUser {

	@Id
	private String username;
	
	private String email;
	private String firstName;
	private String lastName;
	private List<Child> children;
	private String address;
	private String city;
	private String zipCode;
	private String country;

	private String salutation;
	
	private Date createdDate;
	private Date updatedDate;
	
	@Transient
	private Role role;
}
