package com.portal.api.model;

import lombok.Data;

@Data
public class StartEnd {
	
	public StartEnd(String start, String end, double threshold) {
		super();
		this.start = start;
		this.end = end;
		this.threshold = threshold;
	}
	
    private String start;
	private String end;
	private double threshold;

    // constructor, getters, setters, etc...
}