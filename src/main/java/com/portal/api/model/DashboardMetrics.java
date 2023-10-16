package com.portal.api.model;

import lombok.Data;

@Data
public class DashboardMetrics {

	int totalUsers;
	int totalSystemsPurchased;
	int totalCash;
	int attemptsPerDay;
	int attemptsPerMonth;
	int attemptsPerWeek;
	int sessionsPerDay;
	int sessionsPerWeek;
	int sessionsPerMonth;
	int abandonsPerDay;
	int abandonsPerWeek;
	int abandonsPerMonth;
	int totalPlaytime;
	int avgPower;
	int avgFocusComposite;
	int avgImpulseControl;
}
