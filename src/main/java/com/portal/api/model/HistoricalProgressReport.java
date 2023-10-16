package com.portal.api.model;

import java.time.LocalDate;

import lombok.Data;

@Data
public class HistoricalProgressReport {

	LocalDate startDate;
	int daysSinceStart;
	int daysSinceLastAttempt;
	LocalDate projectedCompletionDate;
	int totalAttempts;
	float attemptsPerWeek;
	int sessionsCompleted;
	int missionsCompleted;
	float missionsPerWeek;
	long totalPlaytime;
	long playtimePerWeek;
	long totalPlaytimeCompleted;
	long playtimeCompletedPerWeek;
	int achievements;
}
