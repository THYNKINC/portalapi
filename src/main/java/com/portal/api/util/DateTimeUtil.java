package com.portal.api.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;

import org.opensearch.search.aggregations.metrics.NumericMetricsAggregation.SingleValue;

public abstract class DateTimeUtil {

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

	public static LocalDate isValidLocalDate(String dateStr, String pattern) {
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(pattern);
		LocalDate date = null;
		try {
			return LocalDate.parse(dateStr, dateFormatter);
		} catch (DateTimeParseException e) {
			throw new RuntimeException("Date format exception", e);
		}
	}
}
