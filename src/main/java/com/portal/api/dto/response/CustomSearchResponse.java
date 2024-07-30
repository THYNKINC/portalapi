package com.portal.api.dto.response;

import java.util.Map;

import lombok.Data;

@Data
public class CustomSearchResponse {

	private final Map<String, Long> dishCount;

    // constructor, getters and setters
}