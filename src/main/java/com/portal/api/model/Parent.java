package com.portal.api.model;

import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "parent")
public class Parent {

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
	
	private Date createdDate;
	private Date updatedDate;
}
