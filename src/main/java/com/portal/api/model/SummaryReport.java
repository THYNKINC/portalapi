package com.portal.api.model;

import lombok.Data;

@Data
public class SummaryReport {

	long totalPlayTime;
	SummaryStats runnerStats;
	SummaryStats xferStats;
}
