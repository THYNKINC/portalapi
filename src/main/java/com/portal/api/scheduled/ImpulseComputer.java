package com.portal.api.scheduled;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.portal.api.model.CognitiveSkillsResponse;
import com.portal.api.model.ImpulseControl;
import com.portal.api.model.RunnerSummary;
import com.portal.api.services.AnalyticsService;
import com.portal.api.services.SearchResultsMapper;
import com.portal.api.util.MappingService;
import com.portal.api.util.OpensearchService;

//@Component
public class ImpulseComputer {
	
	private static final Logger logger = LoggerFactory.getLogger(ImpulseComputer.class);

	@Autowired
	private OpensearchService opensearchService;
	
	@Autowired
	private AnalyticsService analytics;
	
	@Scheduled(fixedDelay = 300000)
    private void computeSessions() throws Exception {
        
		logger.info("updating cognitive scores");
        
		// get the last computed session from elastic
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
		
		// Build the search source with the boolean query, the aggregation, and the size
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.size(10000)
				.fetchSource(new String[] {"session_start", "MissionID", "user_id", "metric_type", "metric_value"}, null);

		// Build the search request
		SearchRequest searchRequest = new SearchRequest("collectivemetrics").source(searchSourceBuilder);
		SearchResponse response = opensearchService.search(sslContext, credentialsProvider, searchRequest);
		
		SearchHit[] hits = response.getHits().getHits();

    	// make sure it's sorted in inserted order
    	Map<String, CognitiveSkillsResponse> attempts = new LinkedHashMap<>();
    	Map<String, Integer> missions = new HashMap<>();
    	Map<String, String> users = new HashMap<>();
    	
    	ObjectMapper json = new ObjectMapper()
				  .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    	
    	for (SearchHit hit : hits) {
    		
    		String attemptId = (String) hit.getSourceAsMap().get("session_start");
    		String missionId = (String) hit.getSourceAsMap().get("MissionID");
    		String userId = (String) hit.getSourceAsMap().get("user_id");
    		
    		if (missionId.equals("1.1"))
    			continue;
    		
    		int missionNo = Integer.parseInt(MappingService.getKey(missionId));
    		
    		CognitiveSkillsResponse skills = null;
    		
    		if ((skills = attempts.get(attemptId)) == null) {
    			
    			skills = new CognitiveSkillsResponse();
    			attempts.put(attemptId, skills);
    			missions.put(attemptId, missionNo);
    			users.put(attemptId, userId);
    		}
    		
    		String metricName = (String) hit.getSourceAsMap().get("metric_type");
    		
    		Object o = hit.getSourceAsMap().get("metric_value");
    		Double value = 0d;
    		
    		if (o != null)
    			value = (Double)o ;
    	    
    	    switch (metricName) {
    	    	case "alternating_attention": 
    	    		skills.setAlternatingAttention((int)Math.round(value));
    	    		break;
    	    	case "behavioral_inhibition": 
    	    		skills.setBehavioralInhibition((int)Math.round(value));
    	    		break;
    	    	case "cognitive_inhibition": 
    	    		skills.setCognitiveInhibition((int)Math.round(value));
    	    		break;
    	    	case "delayed_gratification": 
    	    		skills.setDelayOfGratification((int)Math.round(value));
    	    		break;
    	    	case "divided_attention": 
    	    		skills.setDividedAttention((int)Math.round(value));
    	    		break;
    	    	case "focused_attention": 
    	    		skills.setFocusedAttention((int)Math.round(value));
    	    		break;
    	    	case "inner_voice": 
    	    		skills.setInnerVoice((int)Math.round(value));
    	    		break;
    	    	case "interference_control": 
    	    		skills.setInterferenceControl((int)Math.round(value));
    	    		break;
    	    	case "motivational_inhibition": 
    	    		skills.setMotivationalInhibition((int)Math.round(value));
    	    		break;
    	    	case "novelty_inhibition": 
    	    		skills.setNoveltyInhibition((int)Math.round(value));
    	    		break;
    	    	case "selective_attention": 
    	    		skills.setSelectiveAttention((int)Math.round(value));
    	    		break;
    	    	case "self_regulation": 
    	    		skills.setSelfRegulation((int)Math.round(value));
    	    		break;
    	    	case "sustained_attention": 
    	    		skills.setSustainedAttention((int)Math.round(value));
    	    		break;
    	    	default:
    	    		throw new RuntimeException("Unknown metric name: " + metricName);
    	    }
    	}
    	
    	logger.info("Found " + attempts.size() + " sessions to update");
    	
    	int i = attempts.size();
  
    	for (Map.Entry<String, CognitiveSkillsResponse> entry : attempts.entrySet()) {
			
    		logger.info(--i + " sessions left");
    		
    		ImpulseControl composites = 
    				ImpulseControl.fromSkills(
    						entry.getKey(),
    						entry.getValue(),
    						missions.get(entry.getKey()));
    		
    		entry.getValue().setCompositeFocus((int)Math.round(composites.getFocus()));
    		entry.getValue().setCompositeImpulse((int)Math.round(composites.getImpulse()));
    		
    		response = analytics.session(users.get(entry.getKey()), entry.getKey());
    		
    		if (response.getHits().getHits().length == 0) {
    			
    			logger.warn("Session not found: " + entry.getKey() + ", " + users.get(entry.getKey()));
    			continue;
    		}
    		
    		SearchHit session = response.getHits().getHits()[0];
    		
        	RunnerSummary runner = (RunnerSummary)SearchResultsMapper.getSession(session);
        	runner.set_id(session.getId());
        	
        	runner.setScores(entry.getValue());
        	
        	System.out.println(runner);
        	
        	// store back in elastic
			UpdateRequest document = new UpdateRequest("sessions", runner.get_id());
			document.doc(json.writeValueAsString(runner), XContentType.JSON);
	    	
	    	try {
	    		opensearchService.update(sslContext, credentialsProvider, document);
	    	} catch (Exception e) {
	    		logger.error("Invalid record: " + json.writeValueAsString(runner), e);
	    	}
    	}
    }
}

