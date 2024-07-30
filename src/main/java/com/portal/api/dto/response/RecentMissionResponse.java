package com.portal.api.dto.response;

import lombok.Data;

@Data
public class RecentMissionResponse {

	private int missionNumber;
	private String type;
	private String missionStatus;
	private int missionRating;
	private String sessionId;
}
