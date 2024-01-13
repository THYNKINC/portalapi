package com.portal.api.scheduled;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.QueryBuilders;
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
import org.springframework.web.client.RestTemplate;

import com.portal.api.config.Notifications;
import com.portal.api.model.SessionSummary;
import com.portal.api.services.SearchResultsMapper;
import com.portal.api.util.OpensearchService;

@Component
@Profile("prod")
public class IdlePlayerNotifier {
		
	private static final Logger logger = LoggerFactory.getLogger(IdlePlayerNotifier.class);

	@Autowired
	private OpensearchService opensearchService;
	
	@Autowired
	private Notifications notifications;
	
	@Scheduled(cron = "0 0 11 * * MON-FRI")
	private void notifyIdlePlayers() throws Exception {
        
		logger.info("notify idle players");
		
		// get the last computed session from elastic
		SSLContext sslContext = opensearchService.getSSLContext();
		BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
		
		// get last session of all active users (last played less than a month ago)
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
				.query(QueryBuilders
						.boolQuery()
						.must(QueryBuilders.rangeQuery("end_date").gt("now-1M/M")))
				.aggregation(AggregationBuilders
						.terms("players")
						.field("user_id")
						.size(1000)
						.subAggregation(AggregationBuilders
								.topHits("last_session")
								.size(1)
								.sort("start_date", SortOrder.DESC)));

		SearchRequest searchRequest = new SearchRequest("sessions")
				.source(searchSourceBuilder);

		SearchResponse response = opensearchService.search(sslContext, credentialsProvider, searchRequest);
		
		Terms players = response.getAggregations().get("players");
		
		for (Bucket bucket: players.getBuckets()) {
			
			TopHits lastSession = bucket.getAggregations().get("last_session");
			SessionSummary session = SearchResultsMapper.getSession(lastSession.getHits().getHits()[0]);
			
			// exclude sessions more recent than 2 days
			LocalDateTime from = LocalDateTime.ofEpochSecond(session.getEndDate() / 1000, 0, ZoneOffset.UTC);
			LocalDateTime to = LocalDateTime.now();
			long daysBetween = ChronoUnit.DAYS.between(from, to);
	        long businessDays = daysBetween + 1; // Including the start date

	        for (LocalDateTime date = from; !date.isAfter(to); date = date.plusDays(1)) {
	            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
	                businessDays--;
	            }
	        }
	        
	        if (businessDays <= 2)
	        	continue;
	        	
			// exclude users who have reached the end
			if (session.getMissionId() == 15 && session.getType().equals("transference") && session.getStatus().equals("PASS"))
				continue;
		
			logger.info(String.format("Idle player found: [%s] %s:%s %d days ago", session.getUserId(), session.getType(), session.getMissionId(), businessDays));
			
			boolean weekly = businessDays % 5 == 1;
			
			String template = weekly ? notifications.getTplIdleFiveDaysReminder()
					: notifications.getTplIdleTwoDaysReminder();
			
			Map<String, Object> bodyMap = new HashMap<>();
		    bodyMap.put("key", notifications.getMandrillApiKey());
		    bodyMap.put("template_name", template);
		    bodyMap.put("template_content", new ArrayList<>());

		    Map<String, Object> messageMap = new HashMap<>();
		    bodyMap.put("message", messageMap);

		    Map<String, Object> recipient = new HashMap<>();
		    recipient.put("email", session.getParentEmail());
		    recipient.put("name",  session.getParentFirstName() + " " + session.getParentLastName());
		    recipient.put("type", "to");
		    
		    Map<String, Object> bcc = new HashMap<>();
		    bcc.put("email", notifications.getBccEmail());
		    bcc.put("name",  "Notifications");
		    bcc.put("type", "bcc");
		    
		    messageMap.put("to", List.of(recipient, bcc));
		    
		    List<Map<String, String>> varsList = new ArrayList<>();
		    
		    Map<String, String> childNameMap = Map.of("name", "CHILD_NAME", "content", session.getFirstName());
		    varsList.add(childNameMap);

		    Map<String, String> parentNameMap = Map.of("name", "PARENT_NAME", "content", session.getParentFirstName());
		    varsList.add(parentNameMap);
		    
		    Map<String, String> durationMap = Map.of("name", "DURATION", "content", String.valueOf(weekly ? businessDays / 5 : businessDays));
		    varsList.add(durationMap);
		    
		    messageMap.put("global_merge_vars", varsList);
		    
		    new RestTemplate().postForLocation(notifications.getMandrillUrl(), bodyMap);
		}
    }
}