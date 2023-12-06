package com.portal.api.services;

import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLContext;

import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.core.CountRequest;
import org.opensearch.client.core.CountResponse;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.script.Script;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.BucketOrder;
import org.opensearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.opensearch.search.aggregations.bucket.histogram.LongBounds;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.opensearch.search.aggregations.metrics.AvgAggregationBuilder;
import org.opensearch.search.aggregations.metrics.Max;
import org.opensearch.search.aggregations.metrics.MaxAggregationBuilder;
import org.opensearch.search.aggregations.metrics.MinAggregationBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.portal.api.model.CustomSearchResponse;
import com.portal.api.model.RunnerSummary;
import com.portal.api.model.SessionSummary;
import com.portal.api.util.OpensearchService;

@Component
public class AnalyticsService {

	private final OpensearchService opensearchService;
	
	private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

	@Autowired
	public AnalyticsService(OpensearchService opensearchService) {
		this.opensearchService = opensearchService;
	}

	private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private String loadFromFile(String filename) throws IOException {
		
		Resource resource = new ClassPathResource(filename);
		return new String(Files.readAllBytes(resource.getFile().toPath()));
	}

	public SearchResponse completedSessions(String userId) throws Exception {

		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		// Create queries
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders.termsQuery("event_type", "RunnerEnd", "TransferenceStatsEnd", "PVTEnd"))
				.must(QueryBuilders.matchQuery("user_id", userId));

		DateHistogramAggregationBuilder dateHistogramAgg = AggregationBuilders
				.dateHistogram("documents_per_bucket")
				.field("timestamp")
				.minDocCount(1)
				.fixedInterval(new DateHistogramInterval("12h"));

		// Build the search request
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.aggregation(dateHistogramAgg)
				.size(0);

		SearchRequest searchRequest = new SearchRequest("gamelogs-ref");
		searchRequest.source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}
	
	@CacheEvict(value = "dashboard", allEntries = true)
    @Scheduled(cron = "0 0 2 * * *")
    public void emptyDashboardCache() {
        logger.info("emptying dashboard cache");
    }
	
	@CachePut(value = "dashboard")
    @Scheduled(cron = "0 0 3 * * *")
    public SearchResponse populateDashboardCache() throws Exception {
        logger.info("filling dashboard cache");
        return dashboardMetrics();
    }
	
	@Scheduled(fixedDelay = 300000)
    public void computeSessions() throws Exception {
        
		logger.info("computing sessions");
        
		// get the last computed session from elastic
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		MaxAggregationBuilder ended = AggregationBuilders
				.max("ended")
				.field("end_date");
		
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.aggregation(ended);

		SearchRequest searchRequest = new SearchRequest("sessions")
				.source(searchSourceBuilder);

		SearchResponse response = opensearchService.search(sslContext, credentialsProvider, searchRequest);
		
		Max lastProcessed = response.getAggregations().get("ended");
		
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		
		// get all new sessions which ended after the last session end date
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders.termsQuery("event_type", "TransferenceStatsEnd", "RunnerEnd", "PVTEnd", "Abandonned"));
		
		if (lastProcessed.value() > 0)
			boolQuery.must(QueryBuilders.rangeQuery("timestamp")
					.gt(df.format(new Date((long)lastProcessed.value()))));
			
		// Specify the fields to return
		String[] includeFields = new String[] { "session_start", "session_type", "user_id", "event_type" };
		String[] excludeFields = new String[] {};
		searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.fetchSource(includeFields, excludeFields)
				.size(1000)
				.sort("timestamp", SortOrder.ASC);

		searchRequest = new SearchRequest("gamelogs")
				.source(searchSourceBuilder);
		
		response = opensearchService.search(sslContext, credentialsProvider, searchRequest);
		
		ObjectMapper json = new ObjectMapper()
				  .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
		
		logger.info("Adding " + response.getHits().getTotalHits() + " sessions");
		
		int i = 0;
		
		// compute that session
		for (SearchHit hit : response.getHits().getHits()) {
			
			i++;
			
			if (i % 100 == 0)
				logger.info("Added " + i + " sessions");
			
			String sessionType = (String)hit.getSourceAsMap().get("session_type");
			String sessionId = (String)hit.getSourceAsMap().get("session_start");
			String username = (String)hit.getSourceAsMap().get("user_id");
			
			SessionSummary summary = null;
			
			if (sessionType == null) {
				logger.warn(hit.getSourceAsString());
				continue;
			}
			
			switch (sessionType) {
			
			case "runner":
				
				response = runner(username, sessionId);
		    	summary = SearchResultsMapper.getRunner(response, username, sessionId);
		    	break;
		    
			case "transference":

				response = transference(username, sessionId);
		    	summary = SearchResultsMapper.getTransference(response, username, sessionId);
		    	break;
		    	
			case "pvt":
				response = pvt(username, sessionId);
		    	summary = SearchResultsMapper.getPvt(response, username, sessionId);
		    	break;
		    	
		    default:
		    	throw new RuntimeException("session type not supported " + sessionType);
			}
			
			// store back in elastic
			IndexRequest document = new IndexRequest("sessions");
	    	document.source(json.writeValueAsString(summary), XContentType.JSON);
	    	opensearchService.insert(sslContext, credentialsProvider, document);
		}
    }
    
    @Cacheable("dashboard")
	public SearchResponse dashboardMetrics() throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
		
		// 7 days aggregates
		FilterAggregationBuilder days = AggregationBuilders
				.filter("range", QueryBuilders
						.rangeQuery("timestamp")
						.gte("now-7d/d"))
				.subAggregation(AggregationBuilders
						.dateHistogram("daily")
						.field("timestamp")
						.dateHistogramInterval(DateHistogramInterval.days(1))
						.extendedBounds(new LongBounds("now-7d", "now"))
						.subAggregation(AggregationBuilders
								.avg("power")
								.field("Score"))
						.subAggregation(AggregationBuilders
								.filter("attempts", QueryBuilders.termsQuery("event_type", List.of("RunnerEnd", "TransferenceStatsEnd", "PVTEnd")))
								.subAggregation(AggregationBuilders
										.dateHistogram("sessions")
										.field("timestamp")
										.dateHistogramInterval(DateHistogramInterval.hours(12))))
						.subAggregation(AggregationBuilders
								.filter("starts", QueryBuilders.termsQuery("event_type", List.of("RunnerStart", "TransferenceStatsStart"))))
						// TODO this is supposed to be missions but mission 1 doesn't have transference so...
						.subAggregation(AggregationBuilders
								.filter("missions", QueryBuilders.termsQuery("event_type", List.of("TransferenceStatsEnd", "PVTEnd")))));
		
		// totals
		FilterAggregationBuilder active = AggregationBuilders
				.filter("active", QueryBuilders.existsQuery("MissionID"))
				.subAggregation(AggregationBuilders
					.terms("sessions")
					// TODO obviously that's not gonna scale, playtime total and number of users should be running count
					.size(100000)
					.field("session_start.keyword")
					// here we don't know what the last event for that session was so we take the max duration
					.subAggregation(AggregationBuilders
						.max("duration")
						// limit to 30 min max to avoid weird unfinished sessions
						.script(new Script("Math.min(30*60000, doc['timestamp'].value.getMillis() - doc['session_start'].value.getMillis())"))))
				.subAggregation(AggregationBuilders
						.cardinality("users")
						.field("user_id"));
		
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.aggregation(days);
		searchSourceBuilder.aggregation(active);
		
		searchSourceBuilder.size(0);

		SearchRequest searchRequest = new SearchRequest("gamelogs-ref");
		searchRequest.source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}
	
    public SearchResponse historicalProgress(String userId) throws Exception {
    	
    	return historicalProgress(userId, true);
    }
    
	public SearchResponse historicalProgress(String userId, boolean includePlayTime) throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
		
		BoolQueryBuilder boolQueryBuilder = QueryBuilders
				.boolQuery()
				.must(QueryBuilders.matchQuery("user_id", userId));
		
		MinAggregationBuilder startDate = AggregationBuilders
				.min("startDate")
				.field("timestamp");
		
		FilterAggregationBuilder attempts = AggregationBuilders
			.filter("attempts", QueryBuilders.termsQuery("event_type", List.of("RunnerEnd", "TransferenceStatsEnd", "PVTEnd")))
			.subAggregation(AggregationBuilders
					.max("lastAttempt")
					.field("timestamp"))
			.subAggregation(AggregationBuilders
					.dateHistogram("sessions")
					.field("timestamp")
					.dateHistogramInterval(DateHistogramInterval.hours(12))
					.minDocCount(1));
		
			// this is a slow operation, don't run if not needed
			if (includePlayTime) {
			
				// here we're only looking at the end event, so no need to aggregate further
				attempts.subAggregation(AggregationBuilders
					.sum("playtime")
					// limit to 30 min max to avoid weird unfinished sessions
					.script(new Script("Math.min(30*60000, doc['timestamp'].value.getMillis() - doc['session_start'].value.getMillis())")));
			}
		
		FilterAggregationBuilder missions = AggregationBuilders
			.filter("missions", QueryBuilders.termsQuery("event_type", List.of("TransferenceStatsEnd", "PVTEnd")))
			.subAggregation(AggregationBuilders
					.cardinality("id-count")
					.field("MissionID"))
			.subAggregation(AggregationBuilders
					.topHits("highest-missions")
					.size(1)
					.sort("MissionID", SortOrder.DESC)
					.fetchSource(new String[] {"MissionID"}, null));
		
		FilterAggregationBuilder active = AggregationBuilders
			.filter("active", QueryBuilders.existsQuery("MissionID"))
			.subAggregation(AggregationBuilders
				.cardinality("starts-count")
				.field("session_start.keyword"));
		
		// this is a slow operation, don't run if not needed
		if (includePlayTime) {
			active.subAggregation(AggregationBuilders
				.terms("sessions")
				.field("session_start.keyword")
				.size(500)
				// here we don't know what the last event for that session was so we take the max duration
				.subAggregation(AggregationBuilders
					.max("duration")
					// limit to 30 min max to avoid weird unfinished sessions
					.script(new Script("Math.min(30*60000, doc['timestamp'].value.getMillis() - doc['session_start'].value.getMillis())"))));
		}
		
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(boolQueryBuilder);
		searchSourceBuilder.aggregation(startDate);
		searchSourceBuilder.aggregation(attempts);
		searchSourceBuilder.aggregation(missions);
		searchSourceBuilder.aggregation(active);
		
		searchSourceBuilder.size(0);

		SearchRequest searchRequest = new SearchRequest("gamelogs-ref");
		searchRequest.source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}
	
	public SearchResponse summaryStats(String userId) throws Exception {
		return null;
	}
	
	public SearchResponse sessions(String userId) throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
		
		BoolQueryBuilder boolQueryBuilder = QueryBuilders
				.boolQuery()
				.must(QueryBuilders.matchQuery("user_id", userId))
				.must(QueryBuilders.existsQuery("MissionID"));
		
		TermsAggregationBuilder sessions = AggregationBuilders
			.terms("sessions")
			.field("session_start.keyword")
			.size(500)
			.order(BucketOrder.aggregation("started", true))
			.subAggregation(AggregationBuilders
				.topHits("first_event")
				.size(1)
				.sort("timestamp", SortOrder.ASC)
				.fetchSource(new String[] {"timestamp", "session_type", "MissionID", "session_start"}, null))
			.subAggregation(AggregationBuilders
					.filter("actual-end", QueryBuilders
							.boolQuery()
							.mustNot(QueryBuilders
									.termsQuery("event_type", "Abandoned", "LoginSuccess")))
					.subAggregation(AggregationBuilders
						.max("ended")
						.field("timestamp")))
			.subAggregation(AggregationBuilders
				.min("started")
				.field("timestamp"))
			.subAggregation(AggregationBuilders
				.max("power")
				.field("Score"))
			.subAggregation(AggregationBuilders
				.extendedStats("bci")
				.field("bci"))
			.subAggregation(AggregationBuilders
				.avg("tier")
				.field("Tier"))
			.subAggregation(AggregationBuilders
				.terms("stars")
				.field("StarReached")
				.order(BucketOrder.aggregation("at_ts", true))
				.subAggregation(AggregationBuilders
					.min("at_ts")
					.field("timestamp"))
				.subAggregation(AggregationBuilders
					.min("at_score")
					.field("Score")))
			.subAggregation(AggregationBuilders
				.filter("crystals", QueryBuilders.matchQuery("ObjectTypeID", "Token"))
				.subAggregation(AggregationBuilders
					.terms("outcomes")
					.field("event_type")))
			.subAggregation(AggregationBuilders
				.filter("bots", QueryBuilders.boolQuery()
						.must(QueryBuilders.termQuery("ObjectTypeID", "Enemy"))
						.must(QueryBuilders.termsQuery("event_type", "ObjectStatusInRange", "ObjectStatusSelected", "ObjectStatusRejected")))
				.subAggregation(AggregationBuilders
					.terms("results")
					.field("ResultID")
					.subAggregation(AggregationBuilders
						.terms("actions")
						.field("event_type")))
				.subAggregation(AggregationBuilders
					.scriptedMetric("response_time")
					.initScript(new Script("state.total = 0; state.start = 0; state.count = 0;"))
					.mapScript(new Script("if (doc['event_type'].value == 'ObjectStatusInRange') {\r\n"
							+ "            state.start = doc['timestamp'].value.getMillis();\r\n"
							+ "          }\r\n"
							+ "          if (doc['event_type'].value == 'ObjectStatusSelected') {\r\n"
							+ "            state.total += doc['timestamp'].value.getMillis() - state.start;\r\n"
							+ "            state.count++;\r\n"
							+ "          }"))
					.combineScript(new Script("return state.count > 0 ? state.total / state.count : 0;"))
					.reduceScript(new Script("def total = 0;\r\n"
							+ "          def count = 0;\r\n"
							+ "          for (agg in states) {\r\n"
							+ "            total += agg;\r\n"
							+ "            count++;\r\n"
							+ "          }\r\n"
							+ "          def average = (count == 0) ? 0 : total / count;\r\n"
							+ "          return average;"))))
			.subAggregation(AggregationBuilders
				.filter("obstacles", QueryBuilders.matchQuery("ObjectTypeID", "Obstacle"))
				.subAggregation(AggregationBuilders
					.terms("outcomes")
					.field("event_type")))
			.subAggregation(AggregationBuilders
				.filter("completed", QueryBuilders.termsQuery("event_type", "RunnerEnd", "TransferenceStatsEnd", "PVTEnd")))
			.subAggregation(AggregationBuilders
				.filter("decoded", QueryBuilders.termsQuery("event_type", "TransferenceStatsMoleculeDecodeEnd")))
			.subAggregation(AggregationBuilders
					.max("decodes_target")
					.field("TargetDecodes")
					.missing(0));
		
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(boolQueryBuilder);
		searchSourceBuilder.aggregation(sessions);
		
		searchSourceBuilder.size(0);

		SearchRequest searchRequest = new SearchRequest("gamelogs-ref");
		searchRequest.source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}

	public SearchResponse weeklyStats(String userId) throws Exception {

		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, 0); // ! clear would not reset the hour of day !
		cal.clear(Calendar.MINUTE);
		cal.clear(Calendar.SECOND);
		cal.clear(Calendar.MILLISECOND);

		// get start and end of this week in milliseconds
		cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
		String first = df.format(cal.getTime());
		cal.add(Calendar.WEEK_OF_YEAR, 1);
		String last = df.format(cal.getTime());

		QueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("timestamp").gte(first).lt(last);

		BoolQueryBuilder boolQueryBuilder = QueryBuilders
				.boolQuery()
				.must(QueryBuilders.matchQuery("user_id", userId))
				.must(rangeQueryBuilder);
		
		FilterAggregationBuilder starts = AggregationBuilders
				.filter("starts", QueryBuilders.termsQuery("event_type", "RunnerStart", "TransferenceStatsStart", "PVTStart"));
		
		FilterAggregationBuilder attempts = AggregationBuilders
				.filter("attempts", QueryBuilders.termsQuery("event_type", "RunnerEnd", "TransferenceStatsEnd", "PVTEnd"))
				.subAggregation(AggregationBuilders
						.dateHistogram("sessions")
						.field("timestamp")
						.minDocCount(1)
						.fixedInterval(new DateHistogramInterval("12h")));

		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(boolQueryBuilder);
		searchSourceBuilder.aggregation(starts);
		searchSourceBuilder.aggregation(attempts);
		searchSourceBuilder.size(0);

		SearchRequest searchRequest = new SearchRequest("gamelogs-ref");
		searchRequest.source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}

	public SearchResponse completedMissions(String userId) throws Exception {

		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		// Build the match queries
		QueryBuilder eventTypeQuery = QueryBuilders
				.termsQuery("event_type", "TransferenceStatsEnd", "PVTEnd");
		QueryBuilder userIdQuery = QueryBuilders
				.matchQuery("user_id", userId);

		// Combine the match queries into a boolean query
		BoolQueryBuilder boolQuery = QueryBuilders
				.boolQuery()
				.must(eventTypeQuery)
				.must(userIdQuery);

		// Build the aggregation query
		TermsAggregationBuilder missionsAgg = AggregationBuilders
				.terms("missions")
				.field("TaskID")
				.size(15);

		// Build the search source with the boolean query, the aggregation, and the size
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.aggregation(missionsAgg)
				.size(0)
				.fetchSource(false);

		// Build the search request
		SearchRequest searchRequest = new SearchRequest("gamelogs-ref").source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}

	public SearchResponse attemptsPerMission(String userId, String missionId) throws Exception {

		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		SearchRequest searchRequest = new SearchRequest("gamelogs-ref");

		// Create queries
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().must(QueryBuilders.termQuery("TaskID", missionId))
				.must(QueryBuilders.termsQuery("event_type", "RunnerEnd", "TransferenceStatsEnd", "PVTEnd"))
				.must(QueryBuilders.matchQuery("user_id", userId));

		// Set up the source builder
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(boolQuery);
		searchSourceBuilder.size(20);
		searchSourceBuilder.sort("timestamp", SortOrder.DESC);

		// Add the fields to the request
		searchSourceBuilder.fetchSource(new String[] { "session_start", "event_type" }, new String[] {});

		// Add the source builder to the request
		searchRequest.source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}

	/**
	 * Returns the last attempt for any session type
	 * 
	 * @param userId
	 * @return
	 * @throws Exception
	 */
	public SearchResponse lastAttempt(String userId) throws Exception {

		return lastNAttempts(userId, 1);
	}
	
	/**
	 * Returns the last n sessions of any type
	 * 
	 * @param userId
	 * @param sessions the number of attempts you want to return
	 * @return the attempts sorted by most recents first
	 * @throws Exception
	 */
	public SearchResponse lastNAttempts(String userId, int sessions) throws Exception {

		return lastNAttemptsOfType(userId, sessions, List.of("RunnerEnd", "TransferenceStatsEnd", "PVTEnd"));
	}
	
	/**
	 * Returns the last n runners
	 * 
	 * @param userId
	 * @param sessions the number of attempts you want to return
	 * @return the attempts sorted by most recents first
	 * @throws Exception
	 */
	public SearchResponse lastNRunners(String userId, int sessions) throws Exception {

		return lastNAttemptsOfType(userId, sessions, List.of("RunnerEnd"));
	}
	
	/**
	 * 
	 * Returns the last attempts of a given type
	 * 
	 * @param userId
	 * @param sessions the number of attempts you want to return
	 * @param eventTypes the list of event_type values you want to filter for (xference or runner for now)
	 * @return the attempts sorted by most recents first
	 * @throws Exception
	 */
	private SearchResponse lastNAttemptsOfType(String userId, int sessions, List<String> eventTypes) throws Exception {

		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		SearchRequest searchRequest = new SearchRequest("gamelogs-ref");

		// Create queries
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders.termsQuery("event_type", eventTypes))
				.must(QueryBuilders.matchQuery("user_id", userId));

		// Set up the source builder
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(boolQuery);
		searchSourceBuilder.size(sessions);
		searchSourceBuilder.sort("timestamp", SortOrder.DESC);

		// Add the fields to the request
		searchSourceBuilder.fetchSource(new String[] { "session_start", "event_type", "TaskID" }, new String[] {});

		// Add the source builder to the request
		searchRequest.source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}
	
	/**
	 * 
	 * Returns the paginated attempts
	 * 
	 * @return the attempts sorted by most recents first
	 * @throws Exception
	 */
	public SearchResponse attempts(int page, int size) throws Exception {

		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		SearchRequest searchRequest = new SearchRequest("gamelogs-ref");

		// Create queries
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders.termsQuery("event_type", List.of("RunnerEnd", "TransferenceStatsEnd", "PVTEnd")));
		
		// Set up the source builder
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(boolQuery);
		searchSourceBuilder.from(size * (page - 1));
		searchSourceBuilder.size(size);
		searchSourceBuilder.sort("timestamp", SortOrder.DESC);

		// Add the fields to the request
		searchSourceBuilder.fetchSource(new String[] { "session_start", "event_type", "TaskID", "user_id" }, new String[] {});

		// Add the source builder to the request
		searchRequest.source(searchSourceBuilder);

		SearchResponse response = opensearchService.search(sslContext, credentialsProvider, searchRequest);

//		response = attempts(response);
//		response.getHits().getTotalHits();
		
		return response;
	}
	
	public SearchResponse attempts(SearchResponse attempts) throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
		
		QueryBuilder sessionQuery = QueryBuilders
				.termsQuery("session_start.keyword", parseAttempts(attempts));
		
		TermsAggregationBuilder sessions = AggregationBuilders
			.terms("sessions")
			.field("session_start.keyword")
			// we know there's gonna be only as many as the number of attempts passed
			.size(attempts.getHits().getHits().length)
			.order(BucketOrder.aggregation("started", false))
			.subAggregation(AggregationBuilders
					.min("started")
					.field("timestamp"))
			.subAggregation(AggregationBuilders
				.topHits("first_event")
				.size(1)
				.sort("timestamp", SortOrder.ASC)
				.fetchSource(new String[] {"timestamp", "session_type", "MissionID", "session_start", "user_id"}, null))
			.subAggregation(AggregationBuilders
				.max("stars")
				.field("StarReached"))
			.subAggregation(AggregationBuilders
				.filter("decoded", QueryBuilders.termsQuery("event_type", "TransferenceStatsMoleculeDecodeEnd")))
			.subAggregation(AggregationBuilders
					.max("decodes_target")
					.field("TargetDecodes")
					.missing(0));
		
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(sessionQuery);
		searchSourceBuilder.aggregation(sessions);
		
		searchSourceBuilder.size(0);

		SearchRequest searchRequest = new SearchRequest("gamelogs-ref");
		searchRequest.source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}

	public SearchResponse power(String userId, String sessionId) throws Exception {
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		// Build the match queries
		QueryBuilder sessionStartQuery = QueryBuilders.matchQuery("session_start.keyword", sessionId);
		QueryBuilder sessionTypeQuery = QueryBuilders.matchQuery("session_type", "runner");
		QueryBuilder userIdQuery = QueryBuilders.matchQuery("user_id", userId);

		// Combine the match queries into a boolean query
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().must(sessionStartQuery).must(sessionTypeQuery)
				.must(userIdQuery);

		// Build the aggregation queries
		MaxAggregationBuilder powerAgg = AggregationBuilders
				.max("power")
				.field("Score");
		
		DateHistogramAggregationBuilder intervalsAgg = AggregationBuilders
				.dateHistogram("intervals")
				.field("timestamp")
				.fixedInterval(DateHistogramInterval.seconds(30))
				.subAggregation(powerAgg);

		// Build the search source with the boolean query, the aggregation, and the size
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.aggregation(intervalsAgg)
				.size(0)
				.fetchSource(false);

		// Build the search request
		SearchRequest searchRequest = new SearchRequest("gamelogs-ref").source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}

	public SearchResponse attemptCognitiveSkills(String userId, String sessionId) throws Exception {
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		// Build the match queries
		QueryBuilder sessionStartQuery = QueryBuilders.matchQuery("session_start.keyword", sessionId);
		QueryBuilder userIdQuery = QueryBuilders.matchQuery("user_id", userId);

		// Combine the match queries into a boolean query
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().must(sessionStartQuery).must(userIdQuery);

		// Build the search source with the boolean query, the aggregation, and the size
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.fetchSource(new String[] { "metric_value", "metric_type" }, null)
				.size(13);

		// Build the search request
		SearchRequest searchRequest = new SearchRequest("collectivemetrics").source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}
	
	public List<String> parseAttempts(SearchResponse response) {
		
		return Stream
			.of(response.getHits().getHits())
			.map(hit -> (String)hit.getSourceAsMap().get("session_start"))
			.collect(Collectors.toList());
	}
	
	public SearchResponse avgCognitiveSkills(String userId) throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		// Build the match queries
		QueryBuilder userIdQuery = QueryBuilders
				.matchQuery("user_id", userId);
		
		SearchResponse sessions = lastNRunners(userId, 1000);

		QueryBuilder sessionQuery = QueryBuilders
				.termsQuery("session_start.keyword", parseAttempts(sessions));

		// Combine the match queries into a boolean query
		BoolQueryBuilder boolQuery = QueryBuilders
				.boolQuery()
				.must(userIdQuery)
				.must(sessionQuery);

		AvgAggregationBuilder averageAgg = AggregationBuilders
				.avg("average")
				.field("metric_value");
		
		TermsAggregationBuilder typeAgg = AggregationBuilders
				.terms("metrics")
				.field("metric_type")
				.subAggregation(averageAgg);
		
		// Build the search source with the boolean query, the aggregation, and the size
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.size(0)
				.aggregation(typeAgg)
				.fetchSource(false);

		// Build the search request
		SearchRequest searchRequest = new SearchRequest("collectivemetrics").source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}
	
	public SearchResponse allCognitiveSkills(String userId) throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		// Build the match queries
		QueryBuilder userIdQuery = QueryBuilders
				.matchQuery("user_id", userId);
		
		SearchResponse sessions = lastNRunners(userId, 1000);

		QueryBuilder sessionQuery = QueryBuilders
				.termsQuery("session_start.keyword", parseAttempts(sessions));

		// Combine the match queries into a boolean query
		BoolQueryBuilder boolQuery = QueryBuilders
				.boolQuery()
				.must(userIdQuery)
				.must(sessionQuery);
		
		// Build the search source with the boolean query, the aggregation, and the size
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.size(1000)
				.sort("session_start", SortOrder.ASC)
				.sort("metric_type", SortOrder.ASC)
				.fetchSource(new String[] {"session_start", "MissionID", "metric_type", "metric_value"}, null);

		// Build the search request
		SearchRequest searchRequest = new SearchRequest("collectivemetrics").source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}

	public SearchResponse starValues(String userId, String sessionId) throws Exception {

		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		// Build the match queries
		QueryBuilder sessionStartQuery = QueryBuilders.matchQuery("session_start.keyword", sessionId);
		QueryBuilder eventTypeQuery = QueryBuilders.matchQuery("event_type", "RunnerStart");
		QueryBuilder userIdQuery = QueryBuilders.matchQuery("user_id", userId);

		// Combine the match queries into a boolean query
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().must(sessionStartQuery).must(eventTypeQuery)
				.must(userIdQuery);

		// Build the search source with the boolean query, and the size
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(boolQuery).size(1)
				.fetchSource(new String[] { "StarValues" }, new String[] {});
		;

		// Build the search request
		SearchRequest searchRequest = new SearchRequest("gamelogs-ref").source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}

	public SearchResponse attention(String userId, String sessionId) throws Exception {

		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		QueryBuilder sessionStartQuery = QueryBuilders.matchQuery("session_start.keyword", sessionId);
		QueryBuilder userIdQuery = QueryBuilders.matchQuery("user_id", userId);

		// Combine the match queries into a boolean query
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().must(sessionStartQuery).must(userIdQuery);

		AvgAggregationBuilder averageBciAgg = AggregationBuilders
				.avg("average_bci")
				.field("bci");
		
		DateHistogramAggregationBuilder intervalsAgg = AggregationBuilders
				.dateHistogram("intervals")
				.field("timestamp")
				.fixedInterval(DateHistogramInterval.seconds(10))
				.subAggregation(averageBciAgg);

		// Build the search source with the boolean query, the aggregation, and the size
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(boolQuery).aggregation(intervalsAgg)
				.size(0).fetchSource(false);

		// Build the search request
		SearchRequest searchRequest = new SearchRequest("gamelogs-ref").source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}

	public CustomSearchResponse frozenDishes(String userId, String sessionId) throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders
						.termQuery("session_start.keyword", sessionId))
				.must(QueryBuilders
						.termsQuery("event_type", "TransferenceStatsDishStart", "TransferenceStatsDishCorrupted"))
				.must(QueryBuilders.matchQuery("user_id", userId));

		// Build the aggregation
		TermsAggregationBuilder aggregation = AggregationBuilders
				.terms("dish_count")
				.field("event_type");

		// Build the search source
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.aggregation(aggregation)
				.size(0);

		// Create the search request
		SearchRequest searchRequest = new SearchRequest("gamelogs-ref")
				.source(searchSourceBuilder);

		SearchResponse searchResponse = opensearchService.search(sslContext, credentialsProvider, searchRequest);

		Terms dishCountAggregation = searchResponse
				.getAggregations()
				.get("dish_count");
		
		Map<String, Long> dishCount = dishCountAggregation
				.getBuckets()
				.stream()
				.collect(Collectors.toMap(Terms.Bucket::getKeyAsString, Terms.Bucket::getDocCount));

		CustomSearchResponse customResponse = new CustomSearchResponse(dishCount);

		return customResponse;
	}

	public CountResponse decodedMolecules(String userId, String sessionId) throws Exception {

		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
				.must(new MatchQueryBuilder("session_start.keyword", sessionId))
				.must(new TermQueryBuilder("event_type", "TransferenceStatsMoleculeDecodeEnd"))
				.must(QueryBuilders.matchQuery("user_id", userId));

		CountRequest countRequest = new CountRequest("gamelogs-ref");
		countRequest.query(boolQueryBuilder);

		return opensearchService.count(sslContext, credentialsProvider, countRequest);
	}

	public SearchResponse transferenceEvents(String userId, String sessionId) throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery("session_start.keyword", sessionId))
				.must(QueryBuilders.termsQuery("event_type", "TransferenceStatsStart", "TransferenceStatsDishStart", "TransferenceStatsMoleculeDecodeStart", "TransferenceStatsMoleculeDecodeEnd", "TransferenceStatsDishEnd", "TransferenceStatsDishCorrupted"))
				.must(QueryBuilders.matchQuery("user_id", userId));

		// Specify the fields to return
		String[] includeFields = new String[] { "timestamp", "event_type", "DecodeThreshold", "TargetDecodes" };
		String[] excludeFields = new String[] {};
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.fetchSource(includeFields, excludeFields)
				.size(1000)
				.sort("timestamp", SortOrder.ASC);

		// Create the search request
		SearchRequest searchRequest = new SearchRequest("gamelogs-ref").source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}
	
	public SearchResponse runner(String userId, String sessionId) throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
		
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders.termsQuery("session_type", "runner"))
				.must(QueryBuilders.matchQuery("user_id", userId));

		AggregationBuilder sessions = AggregationBuilders
				.filter("session", QueryBuilders.termsQuery("session_start.keyword", sessionId))
				.subAggregation(AggregationBuilders
						.min("started")
						.field("timestamp"))
				.subAggregation(AggregationBuilders
						.topHits("first_event")
						.size(1)
						.sort("timestamp", SortOrder.ASC)
						.fetchSource(new String[] {"timestamp", "session_type", "MissionID", "session_start"}, null))
					.subAggregation(AggregationBuilders
							.filter("actual-end", QueryBuilders
									.boolQuery()
									.mustNot(QueryBuilders
											.termsQuery("event_type", "Abandoned", "LoginSuccess")))
							.subAggregation(AggregationBuilders
								.max("ended")
								.field("timestamp")))
				.subAggregation(AggregationBuilders
					.max("power")
					.field("Score"))
				.subAggregation(AggregationBuilders
					.extendedStats("bci")
					.field("bci"))
				.subAggregation(AggregationBuilders
					.avg("tier")
					.field("Tier"))
				.subAggregation(AggregationBuilders
					.terms("stars")
					.field("StarReached")
					.order(BucketOrder.aggregation("at_ts", true))
					.subAggregation(AggregationBuilders
						.min("at_ts")
						.field("timestamp"))
					.subAggregation(AggregationBuilders
						.min("at_score")
						.field("Score")))
				.subAggregation(AggregationBuilders
					.filter("crystals", QueryBuilders.matchQuery("ObjectTypeID", "Token"))
					.subAggregation(AggregationBuilders
						.terms("outcomes")
						.field("event_type")))
				.subAggregation(AggregationBuilders
					.filter("bots", QueryBuilders.boolQuery()
							.must(QueryBuilders.termQuery("ObjectTypeID", "Enemy"))
							.must(QueryBuilders.termsQuery("event_type", "ObjectStatusInRange", "ObjectStatusSelected", "ObjectStatusRejected")))
					.subAggregation(AggregationBuilders
						.terms("results")
						.field("ResultID")
						.subAggregation(AggregationBuilders
							.terms("actions")
							.field("event_type")))
					.subAggregation(AggregationBuilders
						.scriptedMetric("response_time")
						.initScript(new Script("state.total = 0; state.start = 0; state.count = 0;"))
						.mapScript(new Script("if (doc['event_type'].value == 'ObjectStatusInRange') {\r\n"
								+ "            state.start = doc['timestamp'].value.getMillis();\r\n"
								+ "          }\r\n"
								+ "          if (doc['event_type'].value == 'ObjectStatusSelected') {\r\n"
								+ "            state.total += doc['timestamp'].value.getMillis() - state.start;\r\n"
								+ "            state.count++;\r\n"
								+ "          }"))
						.combineScript(new Script("return state.count > 0 ? state.total / state.count : 0;"))
						.reduceScript(new Script("def total = 0;\r\n"
								+ "          def count = 0;\r\n"
								+ "          for (agg in states) {\r\n"
								+ "            total += agg;\r\n"
								+ "            count++;\r\n"
								+ "          }\r\n"
								+ "          def average = (count == 0) ? 0 : total / count;\r\n"
								+ "          return average;"))))
				.subAggregation(AggregationBuilders
					.filter("obstacles", QueryBuilders.matchQuery("ObjectTypeID", "Obstacle"))
					.subAggregation(AggregationBuilders
						.terms("outcomes")
						.field("event_type")))
				.subAggregation(AggregationBuilders
					.filter("completed", QueryBuilders.termsQuery("event_type", "RunnerEnd")));
		
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(boolQuery);
		searchSourceBuilder.aggregation(sessions);
		
		searchSourceBuilder.size(0);

		SearchRequest searchRequest = new SearchRequest("gamelogs-ref");
		searchRequest.source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}
	
	public SearchResponse pvt(String userId, String sessionId) throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
		
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders.termsQuery("session_type", "pvt"))
				.must(QueryBuilders.matchQuery("user_id", userId));

		AggregationBuilder sessions = AggregationBuilders
				.filter("session", QueryBuilders.termsQuery("session_start.keyword", sessionId))
				.subAggregation(AggregationBuilders
						.min("started")
						.field("timestamp"))
				.subAggregation(AggregationBuilders
						.topHits("first_event")
						.size(1)
						.sort("timestamp", SortOrder.ASC)
						.fetchSource(new String[] {"timestamp", "session_type", "MissionID", "session_start"}, null))
					.subAggregation(AggregationBuilders
							.filter("actual-end", QueryBuilders
									.boolQuery()
									.mustNot(QueryBuilders
											.termsQuery("event_type", "Abandoned", "LoginSuccess")))
							.subAggregation(AggregationBuilders
								.max("ended")
								.field("timestamp")))
				.subAggregation(AggregationBuilders
					.extendedStats("bci")
					.field("bci"))
				.subAggregation(AggregationBuilders
					.filter("completed", QueryBuilders.termsQuery("event_type", "PVTEnd")));
		
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(boolQuery);
		searchSourceBuilder.aggregation(sessions);
		
		searchSourceBuilder.size(0);

		SearchRequest searchRequest = new SearchRequest("gamelogs-ref");
		searchRequest.source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}
	
	public SearchResponse transference(String userId, String sessionId) throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders.termsQuery("session_type", "transference"))
				.must(QueryBuilders.matchQuery("user_id", userId));

		AggregationBuilder sessions = AggregationBuilders
				.filter("session", QueryBuilders.termsQuery("session_start.keyword", sessionId))
				.subAggregation(AggregationBuilders
						.topHits("first_event")
						.size(1)
						.sort("timestamp", SortOrder.ASC)
						.fetchSource(new String[] {"timestamp", "session_type", "MissionID", "session_start"}, null))
					.subAggregation(AggregationBuilders
							.filter("actual-end", QueryBuilders
									.boolQuery()
									.mustNot(QueryBuilders
											.termsQuery("event_type", "Abandoned", "LoginSuccess")))
							.subAggregation(AggregationBuilders
								.max("ended")
								.field("timestamp")))
				.subAggregation(AggregationBuilders
						.min("started")
						.field("timestamp"))
				.subAggregation(AggregationBuilders
						.extendedStats("bci")
						.field("bci"))
				.subAggregation(AggregationBuilders
						.max("target")
						.field("TargetDecodes"))
				.subAggregation(AggregationBuilders
						.filter("end_event", QueryBuilders.termsQuery("event_type", "TransferenceStatsEnd")))
				.subAggregation(AggregationBuilders
						.terms("dishes")
						.field("DishID")
						.size(100)
						.order(BucketOrder.aggregation("dish_start", true))
						.subAggregation(AggregationBuilders
								.min("dish_start")
								.field("timestamp"))
						.subAggregation(AggregationBuilders
								.max("dish_end")
								.field("timestamp"))
						.subAggregation(AggregationBuilders
								.filter("decodes", QueryBuilders.termsQuery("event_type", "TransferenceStatsMoleculeDecodeStart", "TransferenceStatsMoleculeDecodeEnd"))
								.subAggregation(AggregationBuilders
										.min("decode_start")
										.field("timestamp"))
								.subAggregation(AggregationBuilders
										.max("decode_end")
										.field("timestamp"))
								.subAggregation(AggregationBuilders
										.filter("decoded", QueryBuilders.termsQuery("event_type", "TransferenceStatsMoleculeDecodeEnd"))))
						.subAggregation(AggregationBuilders
								.filter("actions", QueryBuilders.termsQuery("event_type", "TransferenceStatsMoleculeSelected", "TransferenceStatsMoleculeRejected"))
								.subAggregation(AggregationBuilders
										.min("first_action")
										.field("timestamp"))
								.subAggregation(AggregationBuilders
										.max("last_action")
										.field("timestamp"))
								.subAggregation(AggregationBuilders
										.filter("rejections", QueryBuilders.termsQuery("event_type", "TransferenceStatsMoleculeRejected"))))
						.subAggregation(AggregationBuilders
								.filter("display", QueryBuilders.termsQuery("event_type", "TransferenceStatsMoleculeDisplay"))
								.subAggregation(AggregationBuilders
										.min("first_displayed")
										.field("timestamp"))));
						
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.aggregation(sessions)
				.size(0);

		// Create the search request
		SearchRequest searchRequest = new SearchRequest("gamelogs-ref").source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}

	public SearchResponse maxStarReached(String userId, String sessionId) throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery("session_start.keyword", sessionId))
				.must(QueryBuilders.existsQuery("StarReached"))
				.must(QueryBuilders.matchQuery("user_id", userId));

		// Specify the fields to return
		String[] includeFields = new String[] { "StarReached" };
		String[] excludeFields = new String[] {};
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.fetchSource(includeFields, excludeFields)
				.size(1)
				.sort("timestamp", SortOrder.DESC);

		// Create the search request
		SearchRequest searchRequest = new SearchRequest("gamelogs-ref").source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}

	public SearchResponse targetMoleculesDecode(String userId, String sessionId) throws Exception {
		
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery("session_start.keyword", sessionId))
				.must(QueryBuilders.termQuery("event_type", "TransferenceStatsStart"))
				.must(QueryBuilders.matchQuery("user_id", userId));

		// Specify the fields to return
		String[] includeFields = new String[] { "TargetDecodes" };
		String[] excludeFields = new String[] {};
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.fetchSource(includeFields, excludeFields)
				.size(1);

		// Create the search request
		SearchRequest searchRequest = new SearchRequest("gamelogs-ref").source(searchSourceBuilder);

		return opensearchService.search(sslContext, credentialsProvider, searchRequest);
	}
}
