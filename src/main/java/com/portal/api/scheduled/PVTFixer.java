package com.portal.api.scheduled;

import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.filter.Filter;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.metrics.TopHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.portal.api.model.Child;
import com.portal.api.model.Parent;
import com.portal.api.model.SessionSummary;
import com.portal.api.services.AnalyticsService;
import com.portal.api.services.ParentService;
import com.portal.api.services.SearchResultsMapper;
import com.portal.api.util.OpensearchService;

//@Component
public class PVTFixer {
	
	private static final Logger logger = LoggerFactory.getLogger(PVTFixer.class);

	@Autowired
	private OpensearchService opensearchService;
	
	@Autowired
	private ParentService parents;
	
	@Autowired
	private AnalyticsService analytics;
	
	@Scheduled(fixedDelay = 300000)
    private void computeSessions() throws Exception {
        
		logger.info("computing pvts");
        
		// get the last computed session from elastic
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		SearchRequest searchRequest = new SearchRequest("gamelogs")
				.source(new SearchSourceBuilder()
						.query(QueryBuilders
								.boolQuery()
								.must(QueryBuilders
										.termsQuery("session_type", "pvt"))
								.must(QueryBuilders
										.termsQuery("user_id", "stephchild")))
						.aggregation(AggregationBuilders
								.terms("sessions")
								.field("session_start")
								.size(10000)
								.subAggregation(AggregationBuilders
										.filter("start_ends", QueryBuilders
												.termsQuery("event_type", "PVTStart", "PVTEnd")))
								.subAggregation(AggregationBuilders
										.topHits("last_doc")
										.size(1)
										.sort("timestamp", SortOrder.DESC)
										.fetchSource(new String[] {"timestamp", "user_id", "session_start"}, null)))
						.size(0));
		
		SearchResponse response = opensearchService.search(sslContext, credentialsProvider, searchRequest);
		
		Map<String, Parent> parentCache = new HashMap<>();
		
		ObjectMapper json = new ObjectMapper()
				  .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
		
		Terms pvts = response.getAggregations().get("sessions");
		
		logger.info("Adding " + pvts.getBuckets().size() + " sessions");
		
		pvts.getBuckets().forEach(bucket -> {
			
			try {
			
				TopHits lastDoc = bucket.getAggregations().get("last_doc");
				SearchHit hit = lastDoc.getHits().getAt(0);
				
				Filter startEnds = bucket.getAggregations().get("start_ends");
				
				if (startEnds.getDocCount() != 1)
					return;
				
				String sessionId = (String)hit.getSourceAsMap().get("session_start");
				String username = (String)hit.getSourceAsMap().get("user_id");
				
				SessionSummary summary = null;
				
				Parent parent = parentCache.get(username);
				
				if (parent == null) {
					
					parent = parents.getParentByChildName(username);
								
					if (parent == null) {
						
						logger.warn("Orphaned child found during sessions processing: " + username);
						return;
					}				
					
					parentCache.put(username, parent);
				}
				
				Child child = parent.getChildren().stream()
						.filter(c -> c.getUsername().equals(username))
						.findFirst()
						.orElseThrow();
				
				SearchResponse pvt = analytics.pvt(username, sessionId);
				
		    	summary = SearchResultsMapper.getPvt(pvt, username, sessionId);
				
				summary.setFirstName(child.getFirstName());
		    	summary.setLastName(child.getLastName());
		    	summary.setParentFirstName(parent.getFirstName());
		    	summary.setParentLastName(parent.getLastName());
		    	summary.setParentEmail(parent.getEmail());
				
				// store back in elastic
				IndexRequest document = new IndexRequest("sessions");
		    	document.source(json.writeValueAsString(summary), XContentType.JSON);
		    	
		    	logger.info(document.toString());
		    	
		    	opensearchService.insert(sslContext, credentialsProvider, document);
	    	
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
    }
}

