package com.portal.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

	public void addChild(Child child) {
		if (children == null) {
			children = new ArrayList<Child>();
		}
		children.add(child);
	}

	public void removeChild(Child child) {
		if (children != null) {
			children.remove(child);
		}
	}
}
