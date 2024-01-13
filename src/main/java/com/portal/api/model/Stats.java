package com.portal.api.model;

import org.opensearch.search.aggregations.metrics.ExtendedStats;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Stats {

	int min;
	int max;
	int avg;
	int stdDev;
	
	public static Stats map(ExtendedStats stats) {
		
		return new Stats((int)Math.round(stats.getMin()), (int)Math.round(stats.getMax()), (int)Math.round(stats.getAvg()), (int)Math.round(stats.getStdDeviation()));
	}
}
