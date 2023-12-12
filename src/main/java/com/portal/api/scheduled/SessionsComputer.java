package com.portal.api.scheduled;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.metrics.Max;
import org.opensearch.search.aggregations.metrics.MaxAggregationBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.portal.api.model.Child;
import com.portal.api.model.Parent;
import com.portal.api.model.SessionSummary;
import com.portal.api.services.AnalyticsService;
import com.portal.api.services.SearchResultsMapper;
import com.portal.api.util.OpensearchService;
import com.portal.api.util.ParentService;

@Component
@Profile("prod")
public class SessionsComputer {
	
	private static final Logger logger = LoggerFactory.getLogger(SessionsComputer.class);

	@Autowired
	private OpensearchService opensearchService;
	
	@Autowired
	private ParentService parents;
	
	@Autowired
	private AnalyticsService analytics;
	
	@Scheduled(fixedDelay = 300000)
    private void computeSessions() throws Exception {
        
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
				.must(QueryBuilders.termsQuery("event_type", "TransferenceStatsEnd", "RunnerEnd", "PVTEnd", "Abandoned"));
		
		if (lastProcessed.value() > 0)
			boolQuery.must(QueryBuilders.rangeQuery("timestamp")
					.gt(df.format(new Date((long)lastProcessed.value()))));
			
		// Specify the fields to return
		String[] includeFields = new String[] { "session_start", "session_type", "user_id", "event_type" };
		String[] excludeFields = new String[] {};
		searchSourceBuilder = new SearchSourceBuilder()
				.query(boolQuery)
				.fetchSource(includeFields, excludeFields)
				.size(10000)
				.sort("timestamp", SortOrder.ASC);

		searchRequest = new SearchRequest("gamelogs")
				.source(searchSourceBuilder);
		
		response = opensearchService.search(sslContext, credentialsProvider, searchRequest);
		
		Map<String, Parent> parentCache = new HashMap<>();
		
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
			
			Parent parent = parentCache.get(username);
			
			if (parent == null) {
				
				parent = parents.getParentByChildName(username);
							
				if (parent == null) {
					
					logger.warn("Orphaned child found during sessions processing: " + username);
					continue;
				}				
				
				parentCache.put(username, parent);
			}
			
			Child child = parent.getChildren().stream()
					.filter(c -> c.getUsername().equals(username))
					.findFirst()
					.orElseThrow();
			
			switch (sessionType) {
			
			case "runner":
				
				response = analytics.runner(username, sessionId);
		    	summary = SearchResultsMapper.getRunner(response, username, sessionId);
		    	break;
		    
			case "transference":

				response = analytics.transference(username, sessionId);
		    	summary = SearchResultsMapper.getTransference(response, username, sessionId);
		    	break;
		    	
			case "pvt":
				response = analytics.pvt(username, sessionId);
		    	summary = SearchResultsMapper.getPvt(response, username, sessionId);
		    	break;
		    	
		    default:
		    	throw new RuntimeException("session type not supported " + sessionType);
			}
			
			summary.setFirstName(child.getFirstName());
	    	summary.setLastName(child.getLastName());
	    	summary.setParentFirstName(parent.getFirstName());
	    	summary.setParentLastName(parent.getLastName());
	    	summary.setParentEmail(parent.getEmail());
			
			// store back in elastic
			IndexRequest document = new IndexRequest("sessions");
	    	document.source(json.writeValueAsString(summary), XContentType.JSON);
	    	
	    	try {
	    		opensearchService.insert(sslContext, credentialsProvider, document);
	    	} catch (Exception e) {
	    		throw new RuntimeException("Invalid record: " + json.writeValueAsString(summary), e);
	    	}
		}
    }
}

