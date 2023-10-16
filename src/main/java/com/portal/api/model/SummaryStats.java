package com.portal.api.model;

import lombok.Data;

@Data
public class SummaryStats {

	int abandonnedAttempts;
	int abandonnedPlaytime;
	int completedFails;
	int completedFailsPlaytime;
	int completedPasses;
	int completedPassesPlaytime;
}
