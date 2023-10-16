package com.portal.api.model;

import lombok.Data;

@Data
public class PerformanceReportSummary {

	long totalPlayTime;
	SummaryStats runnerStats;
	SummaryStats xferStats;
}
