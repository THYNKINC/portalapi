package com.portal.api.scheduled;

import javax.net.ssl.SSLContext;

import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.lucene.queryparser.flexible.core.builders.QueryBuilder;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.metrics.TopHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.portal.api.services.AnalyticsService;
import com.portal.api.services.ParentService;
import com.portal.api.util.OpensearchService;

//@Component
public class DupSessionsFixer {
	
	private static final Logger logger = LoggerFactory.getLogger(DupSessionsFixer.class);

	@Autowired
	private OpensearchService opensearchService;
	
	@Scheduled(fixedDelay = 300000)
    private void computeSessions() throws Exception {
        
		logger.info("Fixing dupes sessions");
        
		// get the last computed session from elastic
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();

		SearchRequest searchRequest = new SearchRequest("sessions")
				.source(new SearchSourceBuilder()
						.aggregation(AggregationBuilders
								.terms("dupes")
								.field("id")
								.minDocCount(2)
								.size(10000)
								.subAggregation(AggregationBuilders
										.topHits("first")
										.size(1)
										.fetchSource(false))));
		
		SearchResponse response = opensearchService.search(sslContext, credentialsProvider, searchRequest);
		
		Terms dupes = response.getAggregations().get("dupes");
		
		logger.info("Found " + dupes.getBuckets().size() + " sessions");
		
		dupes.getBuckets().forEach(bucket -> {
			
			TopHits first = bucket.getAggregations().get("first");
			
			String keepId = first.getHits().getAt(0).getId();
			
			try {
			
				BoolQueryBuilder query = QueryBuilders
						.boolQuery()
						.must(QueryBuilders
								.termQuery("id", bucket.getKeyAsString()))
						.mustNot(QueryBuilders
								.idsQuery()
								.addIds(keepId));
				
				logger.info(keepId);
				
				DeleteByQueryRequest request = new DeleteByQueryRequest("sessions");
				request.setQuery(query);
				
				opensearchService.delete(sslContext, credentialsProvider, request);
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
    }
}

