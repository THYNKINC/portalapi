package com.portal.api.model;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HistoricalProgressReport {

	LocalDate startDate;
	int daysSinceStart;
	int daysSinceLastAttempt;
	LocalDate projectedCompletionDate;
	int totalAttempts;
	double attemptsPerWeek;
	int sessionsCompleted;
	double sessionsPerWeek;
	int missionsCompleted;
	double missionsPerWeek;
	long totalPlaytime;
	double playtimePerWeek;
	long totalPlaytimeCompleted;
	double playtimeCompletedPerWeek;
	int achievements;
}
