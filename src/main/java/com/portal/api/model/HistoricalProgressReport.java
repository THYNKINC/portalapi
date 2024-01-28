package com.portal.api.model;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.bucket.filter.Filter;
import org.opensearch.search.aggregations.bucket.histogram.Histogram;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.metrics.Cardinality;
import org.opensearch.search.aggregations.metrics.Max;
import org.opensearch.search.aggregations.metrics.Min;
import org.opensearch.search.aggregations.metrics.Sum;
import org.opensearch.search.aggregations.metrics.TopHits;

import com.portal.api.util.MappingService;
import com.portal.api.util.TimeUtil;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HistoricalProgressReport {

	LocalDate startDate;
	LocalDate firstPlayed;
	int daysSinceStart;
	int daysSinceLastAttempt;
	LocalDate projectedCompletionDate;
	int totalAttempts;
	double attemptsPerWeek;
	int sessionsCompleted;
	double sessionsPerWeek;
	int missionsCompleted;
	double missionsPerWeek;
	String totalPlaytime;
	String playtimePerWeek;
	String totalPlaytimeCompleted;
	String playtimeCompletedPerWeek;
	int achievements;
	int starts;
	int abandons;
	int highestMission;
	
	public static HistoricalProgressReport parse(SearchResponse response) throws ParseException {
		
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	
    	Map<String, Aggregation> aggs = response.getAggregations().asMap();
    	
    	Min started = (Min)aggs.get("startDate");
    	Filter attempts = (Filter)aggs.get("attempts");
    	
    	Map<String, Aggregation> attemptAggs = attempts.getAggregations().asMap();
    	Max lastAttempt = (Max)attemptAggs.get("lastAttempt");
    	Histogram sessions = (Histogram)attemptAggs.get("sessions");
    	Sum completedPlaytime = (Sum)attemptAggs.get("playtime");
    	
    	Filter missions = (Filter)aggs.get("missions");
    	Map<String, Aggregation> missionAggs = missions.getAggregations().asMap();
    	Cardinality completedMissions = (Cardinality)missionAggs.get("id-count");
    	TopHits highestMissions = (TopHits)missionAggs.get("highest-missions");
    	String highestMission = null;
    	int highestMissionNo = 0;
    	
    	if (highestMissions.getHits().getHits().length > 0) {
    		highestMission = (String)highestMissions.getHits().getHits()[0].getSourceAsMap().get("MissionID");
    		highestMissionNo = Integer.valueOf(MappingService.getKey(highestMission));
    	}
    	
    	long totalPlaytime = 0;
    	Filter active = (Filter)aggs.get("active");
    	Min firstPlayed = (Min)aggs.get("first-played");
    	
    	if (active.getAggregations().get("sessions") != null) {
	    	Terms sessionGroup = (Terms)active.getAggregations().get("sessions");
	    	
	    	for (Terms.Bucket bucket : sessionGroup.getBuckets()) {
	    		Max duration = bucket.getAggregations().get("duration");
	    		totalPlaytime += duration.getValue();
	    	}
    	}
    	
    	Cardinality startsCount = active.getAggregations().get("starts-count");
    	
    	// TODO use value() instead, already a timestamp
    	long lastAttemptTs = df.parse(lastAttempt.getValueAsString()).getTime();
    	long startDateTs = df.parse(started.getValueAsString()).getTime();
    	
    	long today = new Date().getTime();
    	int totalDays = (int)TimeUnit.DAYS.convert(today - startDateTs, TimeUnit.MILLISECONDS);
    	
    	double weeks = Math.max(totalDays / 7, 1);
    	
    	return builder()
			.abandons((int)startsCount.getValue() - (int)attempts.getDocCount())
    		.achievements(0)
    		.attemptsPerWeek(attempts.getDocCount() / weeks)
    		.daysSinceLastAttempt((int)TimeUnit.DAYS.convert(today - lastAttemptTs, TimeUnit.MILLISECONDS))
    		.daysSinceStart(totalDays)
    		.firstPlayed(LocalDate.ofInstant(Instant.ofEpochMilli(startDateTs), TimeZone
    		        .getDefault().toZoneId()))
    		.highestMission(highestMissionNo)
    		.missionsCompleted((int)completedMissions.value())
    		.missionsPerWeek(completedMissions.value() / weeks)
    		.playtimeCompletedPerWeek(TimeUtil.prettyPrint(completedPlaytime.value() / weeks))
    		.playtimePerWeek(TimeUtil.prettyPrint(totalPlaytime / weeks))
    		.projectedCompletionDate(null)
    		.sessionsCompleted(sessions.getBuckets().size())
    		.sessionsPerWeek(sessions.getBuckets().size() / weeks)
    		.startDate(LocalDate.ofInstant(Instant.ofEpochMilli(startDateTs), TimeZone
    		        .getDefault().toZoneId()))
    		.starts((int)startsCount.getValue())
    		.totalAttempts((int)attempts.getDocCount())
    		.totalPlaytime(TimeUtil.prettyPrint(totalPlaytime))
    		.totalPlaytimeCompleted(TimeUtil.prettyPrint(completedPlaytime.value()))
    		.build();
	}
}
