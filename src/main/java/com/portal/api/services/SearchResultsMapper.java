package com.portal.api.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.filter.Filter;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.opensearch.search.aggregations.metrics.Avg;
import org.opensearch.search.aggregations.metrics.ExtendedStats;
import org.opensearch.search.aggregations.metrics.Max;
import org.opensearch.search.aggregations.metrics.Min;
import org.opensearch.search.aggregations.metrics.ScriptedMetric;
import org.opensearch.search.aggregations.metrics.TopHits;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.portal.api.model.Accuracy;
import com.portal.api.model.Crystals;
import com.portal.api.model.Dish;
import com.portal.api.model.Obstacles;
import com.portal.api.model.PVTSummary;
import com.portal.api.model.RunnerSummary;
import com.portal.api.model.StarEarned;
import com.portal.api.model.TransferenceSummary;
import com.portal.api.util.MappingService;
import com.portal.api.util.TimeUtil;

public class SearchResultsMapper {

	public static PVTSummary getPvt(SearchResponse response, String username, String sessionId) throws Exception {
    	
    	Filter session = response.getAggregations().get("session");
    	Aggregations aggs = session.getAggregations();
    	
    	TopHits firstEvent = aggs.get("first_event");
		Map<String, Object> firstDocFields = firstEvent.getHits().getHits()[0].getSourceAsMap();
		
		Max ended = ((Filter)aggs.get("actual-end")).getAggregations().get("ended");
		Min started = aggs.get("started");
		
		long duration = (long)ended.getValue() - (long)started.getValue();
		// sessions to 30 min in case of abandon
		duration = Math.min(30*60, TimeUnit.SECONDS.convert(duration, TimeUnit.MILLISECONDS));
		
		ExtendedStats bci = aggs.get("bci");
		
		Filter completed = aggs.get("completed");
		
		boolean pass = completed.getDocCount() > 0;
		
		PVTSummary pvt = PVTSummary.builder()
    		.bciAvg((int)Math.round(bci.getAvg()))
    		.bciStdDeviation((int)Math.round(bci.getStdDeviation()))
    		.completed(completed.getDocCount() > 0 ? true : false)
    		.duration((int)duration)
    		.endDate((long)ended.getValue())
    		.id(sessionId)
    		.missionId(1)
    		.status(pass ? "PASS" : "FAIL")
    		.userId(username)
    		.build();
		
		return pvt;
    }
	
    public static TransferenceSummary getTransference(SearchResponse response, String username, String sessionId) throws Exception {
    	
    	Filter session = response.getAggregations().get("session");
    		
    	TopHits firstEvent = session.getAggregations().get("first_event");
		Map<String, Object> firstDocFields = firstEvent.getHits().getHits()[0].getSourceAsMap();
		
		Min sessionStart = session.getAggregations().get("started");
		Max sessionEnd = ((Filter)session.getAggregations().get("actual-end")).getAggregations().get("ended");
		ExtendedStats bci = session.getAggregations().get("bci");
		Filter endEvent = session.getAggregations().get("end_event");
		Max target = session.getAggregations().get("target");
		
		Terms dishes = session.getAggregations().get("dishes");
		
		List<Dish> dishList = new ArrayList<>();
		
		for (Bucket dish: dishes.getBuckets()) {
    		
			Min dishStart = dish.getAggregations().get("dish_start");
			Max dishEnd = dish.getAggregations().get("dish_end");
			
			Filter decodes = dish.getAggregations().get("decodes");
			Min decodeStart = decodes.getAggregations().get("decode_start");
			Max decodeEnd = decodes.getAggregations().get("decode_end");
			Filter decoded = decodes.getAggregations().get("decoded");
			
			Filter actions = dish.getAggregations().get("actions");
			Min firstAction = actions.getAggregations().get("first_action");
			Max lastAction = actions.getAggregations().get("last_action");
			Filter rejections = actions.getAggregations().get("rejections");
			
			Filter display = dish.getAggregations().get("display");
			Min firstDisplayed = display.getAggregations().get("first_displayed");
			
			// order of things is
			// display start - display end - tap first - last selected - decode start - decode end
			
			dishList.add(Dish.builder()
					.decoded((int)decoded.getDocCount())
					.duration(TimeUtil.msToSec(dishStart, dishEnd))
					// Infinity string indicates an absence of min/max
					.decodeTime(decodeStart.getValueAsString() != "Infinity" & decodeEnd.getValueAsString() != "Infinity" ? TimeUtil.msToSec(decodeStart, decodeEnd) : 0)
					.gapTime(decodeStart.getValueAsString() != "Infinity"  ? TimeUtil.msToSec(firstDisplayed, decodeStart) : 0)
					.rejected((int)rejections.getDocCount())
					// here we assume that if there are no rejections, actions are selections
					.selected(rejections.getDocCount() > 0 ? 0 : (int)actions.getDocCount())
					.selectTime(TimeUtil.msToSec(firstDisplayed, lastAction))
					.tapTime(TimeUtil.msToSec(firstDisplayed, firstAction))
					.type(rejections.getDocCount() > 0 ? "rejected" : "selected")
					.build());
		}
		
		// TODO turn into method
		double decodeAvg = dishList.stream()
				.mapToInt(dish -> dish.getDecodeTime())
				// 1511828489 value indicates an absence of min/max
				.filter(i -> Math.abs(i) != 1511828489)
				.average().orElse(0);
		
		double decodeVariance = dishList.stream()
                .map(dish -> dish.getDecodeTime() - decodeAvg)
                .map(i -> i * i)
                .mapToDouble(i -> i).average().orElse(0);
		
		double gapAvg = dishList.stream()
				.mapToInt(dish -> dish.getGapTime())
				.filter(i -> Math.abs(i) != 1511828489)
				.average().orElse(0);
		
		double gapVariance = dishList.stream()
                .map(dish -> dish.getGapTime() - gapAvg)
                .map(i -> i * i)
                .mapToDouble(i -> i).average().orElse(0);
		
		double tapAvg = dishList.stream()
				.mapToInt(dish -> dish.getTapTime())
				.filter(i -> Math.abs(i) != 1511828489)
				.average().orElse(0);
		
		double tapVariance = dishList.stream()
                .map(dish -> dish.getTapTime() - tapAvg)
                .map(i -> i * i)
                .mapToDouble(i -> i).average().orElse(0);
		
		double selectAvg = dishList.stream()
				.mapToInt(dish -> dish.getSelectTime())
				.filter(i -> Math.abs(i) != 1511828489)
				.average().orElse(0);
		
		double selectVariance = dishList.stream()
                .map(dish -> dish.getSelectTime() - selectAvg)
                .map(i -> i * i)
                .mapToDouble(i -> i).average().orElse(0);
		
		int decoded = dishList.stream()
			.mapToInt(dish -> dish.getDecoded())
			.sum();
		
		return TransferenceSummary.builder()
				.id(sessionId)
				.missionId(Integer.valueOf(MappingService.getKey((String)firstDocFields.get("MissionID"))))
	    		.bciAvg((int)bci.getAvg())
				.decodeAvg((int)decodeAvg)
				.decoded(decoded)
				.decodeStdDev((int)Math.sqrt(decodeVariance))
				.dishes(dishList)
				.duration(TimeUtil.msToMin(sessionStart, sessionEnd))
				.endDate((long)sessionEnd.getValue())
				.gapAvg((int)gapAvg)
				.gapStdDev((int)Math.sqrt(gapVariance))
				.pctDecoded((int)(decoded / target.value() * 100))
				.selectAvg((int)selectAvg)
				.selectStdDev((int)Math.sqrt(selectVariance))
				.startDate((long)sessionStart.getValue())
				.status(decoded > target.value() ? "PASS" : "FAIL")
				.completed(endEvent.getDocCount() > 0)
				.tapAvg((int)tapAvg)
				.tapStdDev((int)Math.sqrt(tapVariance))
				.target((int)target.value())
				.userId(username)
	    		.build();
    }
    
    public static RunnerSummary getRunner(SearchResponse response, String username, String sessionId) throws Exception {
    	
    	Filter session = response.getAggregations().get("session");
    	Aggregations aggs = session.getAggregations();
    	
    	TopHits firstEvent = aggs.get("first_event");
		Map<String, Object> firstDocFields = firstEvent.getHits().getHits()[0].getSourceAsMap();
		
		Max ended = ((Filter)aggs.get("actual-end")).getAggregations().get("ended");
		Min started = aggs.get("started");
		
		long duration = (long)ended.getValue() - (long)started.getValue();
		// sessions to 30 min in case of abandon
		duration = Math.min(30*60, TimeUnit.SECONDS.convert(duration, TimeUnit.MILLISECONDS));
		
		Max power = aggs.get("power");
		ExtendedStats bci = aggs.get("bci");
		
		Avg avgTier = aggs.get("tier");
		
		Terms stars = aggs.get("stars");
		List<StarEarned> starsEarned = stars.getBuckets().stream()
			.filter(b -> b.getKeyAsNumber().intValue() > 0)
			.map(b -> {
				Min atSecond = b.getAggregations().get("at_ts");
				Min atScore = b.getAggregations().get("at_score");
				long delta = (long)atSecond.getValue() - (long)started.getValue();
				delta = TimeUnit.SECONDS.convert(delta, TimeUnit.MILLISECONDS);
				return new StarEarned((int)delta, (int)atScore.getValue());
			})
			.collect(Collectors.toList());
		
		Filter bots = aggs.get("bots");
		ScriptedMetric responseTime = bots.getAggregations().get("response_time");
		
		Terms results = bots.getAggregations().get("results");
		
		int cs = 0;
		int cr = 0;
		int is = 0;
		int ir = 0;
		int impulses = 0;
		
		for(Bucket result: results.getBuckets()) {
			
			Terms actions = result.getAggregations().get("actions");
			
			for (Bucket action: actions.getBuckets()) {
				
				if (result.getKeyAsString().equals("Correct") && action.getKeyAsString().equals("ObjectStatusSelected")) cs = (int)action.getDocCount();
				if (result.getKeyAsString().equals("Correct") && action.getKeyAsString().equals("ObjectStatusRejected")) cr = (int)action.getDocCount();
				if (result.getKeyAsString().equals("Incorrect") && action.getKeyAsString().equals("ObjectStatusSelected")) is = (int)action.getDocCount();
				if (result.getKeyAsString().equals("Incorrect") && action.getKeyAsString().equals("ObjectStatusRejected")) ir = (int)action.getDocCount();
				if (result.getKeyAsString().equals("Impulse")) impulses++;
			}
		}
		
		int opportunities = cs + cr + is + ir + impulses;
		
		Filter crystals = aggs.get("crystals");
		Terms crystalOutcome = crystals.getAggregations().get("outcomes");
		int collectedCrystals = (int) crystalOutcome.getBuckets()
			.stream()
			.filter(b -> b.getKeyAsString().equals("ObjectStatusCollected"))
			.mapToLong(b -> b.getDocCount())
			.sum();
		
		int missedCrystals = (int) crystalOutcome.getBuckets()
			.stream()
			.filter(b -> b.getKeyAsString().equals("ObjectStatusOutOfRange"))
			.mapToLong(b -> b.getDocCount())
			.sum();
		int totalCrystals = collectedCrystals + missedCrystals;
		
		Filter obstacles = aggs.get("obstacles");
		Terms obstacleOutcome = obstacles.getAggregations().get("outcomes");
		int collidedObstacles = (int) obstacleOutcome.getBuckets()
			.stream()
			.filter(b -> b.getKeyAsString().equals("ObjectStatusCollided"))
			.mapToLong(b -> b.getDocCount())
			.sum();
		int avoidedObstacles = (int) crystalOutcome.getBuckets()
			.stream()
			.filter(b -> b.getKeyAsString().equals("ObjectStatusOutOfRange"))
			.mapToLong(b -> b.getDocCount())
			.sum();
		int totalObstacles = collidedObstacles + avoidedObstacles;
		
		Filter completed = aggs.get("completed");
		
		boolean pass = starsEarned.size() > 0;
		
		RunnerSummary runner = RunnerSummary.builder()
    		.accuracy(Accuracy.builder()
    			.opportunities(opportunities)
    			.correctRejected(cr)
    			.correctSelected(cs)
    			.incorrectRejected(ir)
    			.incorrectSelected(is)
    			.impulses(impulses)
    			.build())
    		.badges(null)
    		.bciAvg((int)Math.round(bci.getAvg()))
    		.bciStdDeviation((int)Math.round(bci.getStdDeviation()))
    		.completed(completed.getDocCount() > 0 ? true : false)
    		.duration((int)duration)
    		.endDate((long)ended.getValue())
    		.id(sessionId)
    		.maxPower(Math.round((float)power.value() * 100 / 19808))
    		.missionId(Integer.valueOf(MappingService.getKey((String)firstDocFields.get("MissionID"))))
    		.ranks(null)
    		.responseTime(String.format("%.3f", (int)responseTime.aggregation() / 1000f))
    		.scores(null)
    		.stars(starsEarned)
    		.startDate((long)started.getValue())
    		.status(pass ? "PASS" : "FAIL")
    		.tierAvg(avgTier.getValueAsString().equals("Infinity") ? 0 : (int)avgTier.value())
    		.userId(username)
    		.build();
		
		if (totalCrystals > 0) {
			runner.setCrystals(Crystals.builder()
				.collected(collectedCrystals)
				.pctCollected((int)(collectedCrystals / (float)totalCrystals * 100))
				.missed(missedCrystals)
				.build());
		}
		
		if (totalObstacles > 0) {
    		runner.setObstacles(Obstacles.builder()
				.avoided(avoidedObstacles)
				.collided(collidedObstacles)
				.pctAvoided((int)(avoidedObstacles / (float)totalObstacles * 100))
				.build());
		}
		
		return runner;
    }
}
