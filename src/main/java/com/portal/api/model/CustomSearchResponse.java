package com.portal.api.model;

import java.util.Map;

import lombok.Data;

@Data
public class CustomSearchResponse {

	private final Map<String, Long> dishCount;

    // constructor, getters and setters
}