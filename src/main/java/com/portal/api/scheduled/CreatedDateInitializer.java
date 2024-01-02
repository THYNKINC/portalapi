package com.portal.api.scheduled;

import java.util.Date;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.opensearch.search.aggregations.metrics.Min;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.portal.api.model.Child;
import com.portal.api.util.OpensearchService;
import com.portal.api.util.ParentService;

// @Component
public class CreatedDateInitializer {
	
	private static final Logger logger = LoggerFactory.getLogger(CreatedDateInitializer.class);

	@Autowired
	private OpensearchService opensearchService;
	
	@Autowired
	private ParentService parents;
	
	// every day
	@Scheduled(fixedRate = 86400000)
	private void initPlayers() throws Exception {
        
		logger.info("init player's created dates");
		
		// get the last computed session from elastic
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
		
		// get last session of all active users (last played less than a month ago)
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.aggregation(AggregationBuilders
						.terms("players")
						.field("user_id")
						.size(1000)
						.subAggregation(AggregationBuilders
								.min("started")
								.field("timestamp")));

		SearchRequest searchRequest = new SearchRequest("gamelogs")
				.source(searchSourceBuilder);

		SearchResponse response = opensearchService.search(sslContext, credentialsProvider, searchRequest);
		
		Terms players = response.getAggregations().get("players");
		
		for (Bucket bucket: players.getBuckets()) {
			
			Min started = bucket.getAggregations().get("started");
			String username = bucket.getKeyAsString();
			
			List<Child> children = parents.getChildrenByUsername(List.of(username));
			
			if (children.isEmpty()) {
				logger.warn("Username not found:" + username);
				continue;
			}
			
			if (children.size() >= 2) {
				logger.warn("Duplicate username:" + username);
				continue;
			}
			
			
			Child child = children.get(0);		
			
			logger.info("Updating " + username);
			
			if (child.getCreatedDate() != null)
				continue;
			
			child.setCreatedDate(new Date((long)started.getValue()));
			
			parents.updateChild(child);
		}
    }
}