package com.portal.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Badge {

	private String name;
	private String imageUrl;
	private String description;
}
