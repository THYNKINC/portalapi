package com.portal.api.model;

import java.util.List;

import lombok.Data;

@Data
public class MissionResponse {

	private String status;
	private double rating;
	private List<SessionResponse> sessions;
}