package com.portal.api.model;

import lombok.Data;

@Data
public class RecentMissionResponse {

	private int missionNumber;
	private String missionStatus;
	private int missionRating;
}
