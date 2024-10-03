package com.portal.api.dto.response;

import lombok.Data;

@Data
public class ProgressResponse {

	private int missionsCompleted;
	private int sessionsCompleted;
	private int abandonedAttempts;
	private int sessionsCompletedPerWeek;
	private long lastPlayed;
	private double thynkScore;
	private String playtime;
}
