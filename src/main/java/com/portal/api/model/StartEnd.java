package com.portal.api.model;

import lombok.Data;

@Data
public class StartEnd {
	public StartEnd(String start, String end) {
		super();
		this.start = start;
		this.end = end;
	}
	
    private String start;
	private String end;

    // constructor, getters, setters, etc...
}