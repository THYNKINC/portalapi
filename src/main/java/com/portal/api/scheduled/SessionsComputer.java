package com.portal.api.scheduled;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.UpdateByQueryRequest;
import org.opensearch.script.Script;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.opensearch.search.aggregations.metrics.TopHits;
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
        
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		// is it a first time run?
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.size(1);

		SearchRequest searchRequest = new SearchRequest("sessions")
				.source(searchSourceBuilder);
		
		SearchResponse response = opensearchService.search(sslContext, credentialsProvider, searchRequest);
		
		// get all unique sessions IDs from all the events in gamelogs
		
		// TODO exlcude sessions which we know have already ended (completed from sessions)
		
		// Specify the fields to return
		String[] includeFields = new String[] { "session_start", "session_type", "user_id", "event_type" };
		String[] excludeFields = new String[] {};
		searchSourceBuilder = new SearchSourceBuilder()
				.aggregation(AggregationBuilders
						.terms("unique_sessions")
						.field("session_start.keyword")
						.size(65535)
						.subAggregation(AggregationBuilders
								.topHits("single")
								.size(0)
								.fetchSource(includeFields, excludeFields)))
				.size(0)
				.sort("timestamp", SortOrder.ASC);

		// if not first time, only fetch events which happened (timestamp) within the last hour
		// in-flight sessions always get re-computed until completed or considered abandoned (older than 60 min)
		if (response.getHits().getTotalHits().value > 0)
			searchSourceBuilder.query(QueryBuilders.boolQuery()
					.must(QueryBuilders
							.rangeQuery("timestamp")
							.gte("now-60m/m")));
		
		searchRequest = new SearchRequest("gamelogs")
				.source(searchSourceBuilder);
		
		System.out.println("######### " + searchSourceBuilder.toString() + " ##############");
		
		response = opensearchService.search(sslContext, credentialsProvider, searchRequest);
		
		Map<String, Parent> parentCache = new HashMap<>();
		
		ObjectMapper json = new ObjectMapper()
				  .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
		
		Terms sessionIds = response.getAggregations().get("unique_sessions");
		
		long total = sessionIds.getBuckets().size();
		
		logger.info("Adding " + total + " sessions");
		
		int i = 0;
		
		List<SessionSummary> sessions = new ArrayList<>();
		
		// compute that session
		for (Bucket bucket : sessionIds.getBuckets()) {
			
			TopHits topHits = bucket.getAggregations().get("single");
			
			SearchHit hit = topHits.getHits().getAt(0);
			
			i++;
			
			if (i % 100 == 0)
				logger.info("Added " + i + " sessions");
			
			SessionSummary summary = null;
			
			if (total <= 10) {
				logger.info(hit.getSourceAsString());
			}
			
			String sessionType = (String)hit.getSourceAsMap().get("session_type");
			String sessionId = (String)hit.getSourceAsMap().get("session_start");
			String username = (String)hit.getSourceAsMap().get("user_id");
			
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
		    		
		    		double cs = runner.getAccuracy().getCorrectSelected() / runner.getAccuracy().getOpportunities();
		    		double is = runner.getAccuracy().getIncorrectSelected() / runner.getAccuracy().getOpportunities();
		    		
		            double mean = 0;
		            double standardDev = 1;
		            
		            NormalDistribution distribution = new NormalDistribution(mean, standardDev);
		            double cp1 = distribution.inverseCumulativeProbability(cs);
		            double cp2 = distribution.inverseCumulativeProbability(is);
		            
		    		runner.getScores().setPerformanceScore(cp1  - cp2);
			    	
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
			document.id(username + "_" + sessionId);
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