package com.portal.api.scheduled;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.UpdateByQueryRequest;
import org.opensearch.script.Script;
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
import com.portal.api.model.ImpulseControl;
import com.portal.api.model.Parent;
import com.portal.api.model.RunnerSummary;
import com.portal.api.model.SessionSummary;
import com.portal.api.services.AnalyticsService;
import com.portal.api.services.ParentService;
import com.portal.api.services.SearchResultsMapper;
import com.portal.api.util.OpensearchService;

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
		
		long startedAt = new Date().getTime();
        
		// get the last created session from elastic
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		MaxAggregationBuilder created = AggregationBuilders
				.max("created")
				.field("created_date");
		
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.aggregation(created)
				.size(0);

		SearchRequest searchRequest = new SearchRequest("sessions")
				.source(searchSourceBuilder);

		SearchResponse response = opensearchService.search(sslContext, credentialsProvider, searchRequest);
		
		Max lastCreated = response.getAggregations().get("created");
		
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'");
		
		// get all new sessions which ended after the last session end date
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders.termsQuery("event_type", "TransferenceStatsEnd", "RunnerEnd", "PVTEnd", "Abandoned"));
		
		String lastCreatedDate = df.format(new Date((long)lastCreated.value()));
		
		logger.info("Last processed session end date: " + lastCreatedDate);
		
		if (lastCreated.value() > 0)
			boolQuery
				.must(QueryBuilders.rangeQuery("received_on")
					.gt(lastCreatedDate)
					.includeLower(false));
			
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
		
		long total = response.getHits().getTotalHits().value;
		
		logger.info("Adding " + total + " sessions");
		
		int i = 0;
		
		List<SessionSummary> sessions = new ArrayList<>();
		
		// compute that session
		for (SearchHit hit : response.getHits().getHits()) {
			
			i++;
			
			if (i % 100 == 0)
				logger.info("Added " + i + " sessions");
			
			String sessionType = (String)hit.getSourceAsMap().get("session_type");
			String sessionId = (String)hit.getSourceAsMap().get("session_start");
			String username = (String)hit.getSourceAsMap().get("user_id");
			
			SessionSummary summary = null;
			
			if (total <= 10) {
				logger.info(hit.getSourceAsString());
			}	
			
			if (sessionType == null || "null".equals(sessionType)) {
				logger.warn("Unsupported session type: " + hit.getSourceAsString());
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
			try {
				
				switch (sessionType) {
				
				case "runner":
					
					response = analytics.runner(username, sessionId);
			    	summary = SearchResultsMapper.getRunner(response, username, sessionId);
			    	
			    	SearchResponse searchResponse = analytics.attemptCognitiveSkills(username, sessionId); 		    	
			    	SearchHit[] hits = searchResponse.getHits().getHits();
			    	
			    	RunnerSummary runner = (RunnerSummary)summary;
			    	
			    	runner.setScores(SearchResultsMapper.getCognitiveSkills(hits));
			    	
			    	ImpulseControl composites = 
		    				ImpulseControl.fromSkills(summary.getId(), runner.getScores(), runner.getMissionId());
			    	
		    		runner.getScores().setCompositeFocus((int)Math.round(composites.getFocus()));
		    		runner.getScores().setCompositeImpulse((int)Math.round(composites.getImpulse()));
			    	
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
			    	logger.error("session type not supported " + sessionType);
			    	continue;
				}
			} catch (Exception e) {
				
				logger.error("Error computing session " + sessionId, e);
			}
			
			summary.setFirstName(child.getFirstName());
	    	summary.setLastName(child.getLastName());
	    	summary.setParentFirstName(parent.getFirstName());
	    	summary.setParentLastName(parent.getLastName());
	    	summary.setParentEmail(parent.getEmail());
	    	
	    	summary.setCreatedDate(startedAt);
	    	
	    	sessions.add(summary);
			
			// phase 1, insert session
			IndexRequest document = new IndexRequest("sessions");
	    	document.source(json.writeValueAsString(summary), XContentType.JSON);
	    	
	    	try {
	    		opensearchService.insert(sslContext, credentialsProvider, document);
	    	} catch (Exception e) {
	    		logger.error("Invalid record: " + json.writeValueAsString(summary), e);
	    	}
		}
		
		// phase 2, update gamelogs with mission status
		for (SessionSummary session : sessions) {
			
			logger.info("Updating logs for session {}", session.getId());
			
			UpdateByQueryRequest updateRequest = buildUpdateQuery(session);
			
			try {
	    		opensearchService.updateByQuery(sslContext, credentialsProvider, updateRequest);
	    	} catch (Exception e) {
	    		logger.error("Update failed for session: {}", session, e);
	    	}
		}
    }

	public UpdateByQueryRequest buildUpdateQuery(SessionSummary session) {
		
		String script = String.format("ctx._source.completed = %s; ctx._source.status = '%s';", session.isCompleted(), session.getStatus());
		
		UpdateByQueryRequest updateRequest = new UpdateByQueryRequest("gamelogs");
		
		updateRequest.setQuery(QueryBuilders.boolQuery()
			.must(QueryBuilders.termQuery("session_start", session.getId())));
		updateRequest.setScript(
			new Script(script)
		);
		
		return updateRequest;
	}
}