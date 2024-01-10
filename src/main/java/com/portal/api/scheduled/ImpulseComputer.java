package com.portal.api.scheduled;

import javax.net.ssl.SSLContext;

import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.portal.api.model.ImpulseControl;
import com.portal.api.model.RunnerSummary;
import com.portal.api.services.SearchResultsMapper;
import com.portal.api.util.OpensearchService;

//@Component
public class ImpulseComputer {
	
	private static final Logger logger = LoggerFactory.getLogger(ImpulseComputer.class);

	@Autowired
	private OpensearchService opensearchService;
	
	@Scheduled(fixedDelay = 300000)
    private void computeSessions() throws Exception {
        
		logger.info("updating cognitive scores");
        
		// get the last computed session from elastic
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
		
		// Build the search source with the boolean query, the aggregation, and the size
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(QueryBuilders.boolQuery()
							.must(QueryBuilders
									.termQuery("type", "runner"))
							.must(QueryBuilders.boolQuery()
									.should(QueryBuilders
										.nestedQuery("scores", QueryBuilders
												.rangeQuery("scores.focused_attention")
												.gt(0), ScoreMode.None))
									.should(QueryBuilders
											.nestedQuery("scores", QueryBuilders
													.rangeQuery("scores.sustained_attention")
													.gt(0), ScoreMode.None))))
				.size(10000);

		// Build the search request
		SearchRequest searchRequest = new SearchRequest("sessions").source(searchSourceBuilder);
		SearchResponse response = opensearchService.search(sslContext, credentialsProvider, searchRequest);
		
		SearchHit[] hits = response.getHits().getHits();

    	ObjectMapper json = new ObjectMapper()
				  .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    	
    	logger.info("Found " + hits.length + " incomplete sessions");
    	
    	int i = hits.length;
    	
    	for (SearchHit hit : hits) {
    		
    		logger.info(i-- + " sessions left");
    		
    		RunnerSummary runner = (RunnerSummary)SearchResultsMapper.getSession(hit);
    		runner.set_id(hit.getId());
    		
    		ImpulseControl composites = 
    				ImpulseControl.fromSkills(runner.getId(), runner.getScores(), runner.getMissionId());
	    	
    		runner.getScores().setCompositeFocus((int)Math.round(composites.getFocus()));
    		runner.getScores().setCompositeImpulse((int)Math.round(composites.getImpulse()));
    		
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

