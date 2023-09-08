package com.portal.api.model;

import lombok.Data;

@Data
public class ProgressResponse {

	private int missionsCompleted;
	private int sessionsCompleted;
	private int abandonedAttempts;
	private int sessionsCompletedPerWeek;
	private int thynkScore;
}
