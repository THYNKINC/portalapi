package com.portal.api.util;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.opensearch.search.aggregations.metrics.NumericMetricsAggregation.SingleValue;

public abstract class TimeUtil {

	public static int msToSec(int value) {
		
		return (int)TimeUnit.SECONDS.convert(Integer.valueOf(value).longValue(), TimeUnit.MILLISECONDS);
	}
	
	public static int msToSec(SingleValue first, SingleValue last) {
		
		return (int)TimeUnit.SECONDS.convert((long)(last.value() - first.value()), TimeUnit.MILLISECONDS);
	}
	
	public static int msToMin(SingleValue first, SingleValue last) {
		
		return (int)TimeUnit.MINUTES.convert((long)(last.value() - first.value()), TimeUnit.MILLISECONDS);
	}
	
	/**
	 * 
	 * @param duration milliseconds
	 * @return
	 */
	public static String prettyPrint(long duration) {
		
		return Duration.ofMillis(duration).toString()
				.substring(2)
	            .replaceAll("(\\d[HMS])(?!$)", "$1 ")
	            .toLowerCase();
	}
	
	/**
	 * 
	 * @param duration milliseconds
	 * @return
	 */
	public static String prettyPrint(double duration) {
		
		return prettyPrint((long)duration);
	}
}
