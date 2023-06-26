package com.portal.api.controllers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.portal.api.model.CreateChildRequest;
import com.portal.api.model.CreateUserRequest;
import com.portal.api.model.FogAnalysisResponse;
import com.portal.api.model.GraphResponse;
import com.portal.api.model.LoginRequest;
import com.portal.api.model.MissionResponse;
import com.portal.api.model.Parent;
import com.portal.api.model.PowerResponse;
import com.portal.api.model.ProgressResponse;
import com.portal.api.model.RecentMissionResponse;
import com.portal.api.model.SessionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.api.model.AttentionResponse;
import com.portal.api.model.BadgesResponse;
import com.portal.api.model.Child;
import com.portal.api.util.HttpService;
import com.portal.api.util.JwtService;
import com.portal.api.util.MongoService;
import com.portal.api.util.OpensearchService;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminConfirmSignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;

import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.RequestOptions;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Requests;
import org.opensearch.client.RestClient;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.opensearch.search.aggregations.metrics.AvgAggregationBuilder;
import org.opensearch.search.aggregations.metrics.MaxAggregationBuilder;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;


import javax.validation.Valid;

@RestController
@RequestMapping("/portal")
@Validated
public class PortalController {
	
	@Value("${app-client-id}")
	private String APP_CLIENT_ID;
	
	@Value("${games-port}")
	private String GAMES_PORT;
	
	@Value("${games-service}")
	private String GAMES_SERVICE;
	
	@Value("${group-name-user}")
	private String GROUP_NAME_USER;
	
	@Value("${user-pool-id}")
	private String USER_POOL_ID;
	
	private final JwtService jwtService;
	
	private final MongoService mongoService;
	
	private final OpensearchService opensearchService;

    @Autowired
    public PortalController(
    		JwtService jwtService,
    		MongoService mongoService,
    		OpensearchService opensearchService) {
        this.jwtService = jwtService;
        this.mongoService = mongoService;
        this.opensearchService = opensearchService;
    }
    
    @PostMapping("/login")
    public String login(@RequestBody @Valid LoginRequest loginRequest, HttpServletRequest request) throws Exception {
    	
    	CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .clientId(APP_CLIENT_ID)	//COGNITO_APP_CLIENT_ID            
                .authParameters(
                        Map.of(
                                "USERNAME", loginRequest.getUsername(),
                                "PASSWORD", loginRequest.getPassword()
                        )
                )
                .build();

        InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);
        
        AuthenticationResultType authResult = authResponse.authenticationResult();
        
        return authResult.idToken();
    }
    
    @PostMapping("/signup")
    public void createParent(@Valid @RequestBody CreateUserRequest createUserRequest, HttpServletRequest request) throws Exception {
    	
    	// Create a CognitoIdentityProviderClient
    	CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

    	SignUpRequest signUpRequest = SignUpRequest.builder()
    	        .clientId(APP_CLIENT_ID)
    	        .username(createUserRequest.getEmail())
    	        .password(createUserRequest.getPassword())
    	        .userAttributes(
    	                AttributeType.builder().name("email").value(createUserRequest.getEmail()).build(),
    	                AttributeType.builder().name("family_name").value(createUserRequest.getLastName()).build(),
    	                AttributeType.builder().name("given_name").value(createUserRequest.getFirstName()).build()
    	        )
    	        .build();
    	
    	// Call the signUp method to create the user
    	SignUpResponse signUpResponse = cognitoClient.signUp(signUpRequest);

    	// Access the user's username and other details from the signUpResponse
    	String usern = signUpResponse.userSub();
    	System.out.println("User created with username sub: " + usern);
    	
    	AdminAddUserToGroupRequest addUserToGroupRequest = AdminAddUserToGroupRequest.builder()
    	        .userPoolId(USER_POOL_ID)
    	        .username(createUserRequest.getEmail())
    	        .groupName(GROUP_NAME_USER)
    	        .build();
    	
    	cognitoClient.adminAddUserToGroup(addUserToGroupRequest);
    	
    	AdminConfirmSignUpRequest confirmSignUpRequest = AdminConfirmSignUpRequest.builder()
    	        .userPoolId(USER_POOL_ID)
    	        .username(createUserRequest.getEmail())
    	        .build();
    	
    	AdminConfirmSignUpResponse confirmSignUpResponse = cognitoClient.adminConfirmSignUp(confirmSignUpRequest);

    	boolean isConfirmed = confirmSignUpResponse.sdkHttpResponse().isSuccessful();
    	System.out.println(isConfirmed);
    	
    	Parent parent = new Parent();
    	parent.setChildren(new ArrayList<>());
    	parent.setEmail(createUserRequest.getEmail());
    	parent.setFirstName(createUserRequest.getFirstName());
    	parent.setLastName(createUserRequest.getLastName());
    	parent.setUsername(signUpResponse.userSub());
    	
    	mongoService.upsertParent(parent);
    }

    @PostMapping("/children")
    public void createChild(@Valid @RequestBody CreateChildRequest createChildRequest, HttpServletRequest request) throws Exception {
        
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false);
    	
    	CreateUserRequest createUserRequest = new CreateUserRequest();
    	createUserRequest.setEmail(jwt.getClaim("email"));
    	createUserRequest.setFirstName(createChildRequest.getFirstName());
    	createUserRequest.setLastName(createChildRequest.getLastName());
    	createUserRequest.setParent(jwt.getClaim("cognito:username"));
    	createUserRequest.setPassword(createChildRequest.getPassword());
    	createUserRequest.setUsername(createChildRequest.getUsername());
    	
    	ObjectMapper mapper = new ObjectMapper();

        // Convert the userRequest object to a JSON string
        String requestBody = mapper.writeValueAsString(createUserRequest);
        
        String bearerToken = jwtService.getAdminJwt();
        String result = HttpService.sendHttpPostRequest("http://" + GAMES_SERVICE + ":" + GAMES_PORT + "/games/users", requestBody, bearerToken);
        
        Child child = new Child();
        child.setFirstName(createChildRequest.getFirstName());
        child.setLastName(createChildRequest.getLastName());
        child.setUsername(createChildRequest.getUsername());
        
        //TODO get parent from mongo, add child, save
        mongoService.updateParent(jwt.getClaim("cognito:username"), child);

    }
    
    @GetMapping("/children/{username}/progress")
    public ProgressResponse childProgress(@PathVariable("username") String id, HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false);
    	
    	ProgressResponse progressResponse = new ProgressResponse();
    	progressResponse.setAbandonedAttempts(2);
    	progressResponse.setSessionsCompletedPerWeek(2);
    	progressResponse.setMissionsCompleted(3);
    	progressResponse.setSessionsCompleted(7);
    	
    	return progressResponse;
    }
    
    @GetMapping("/children/{username}/recent-mission")
    public RecentMissionResponse childRecentMission(@PathVariable("username") String id, HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false);
    	
    	RecentMissionResponse recentMissionResponse = new RecentMissionResponse();
    	recentMissionResponse.setMissionNumber(4);
    	recentMissionResponse.setMissionRating(3);
    	recentMissionResponse.setMissionStatus("unknown");
    	
    	return recentMissionResponse;
    }
    
    @GetMapping("/children/{username}/badges")
    public BadgesResponse childBadges(@PathVariable("username") String id, HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false);
    	
    	List<String> badges = new ArrayList<>();
    	badges.add("badge1");
    	badges.add("badge2");
    	badges.add("badge5");
    	
    	BadgesResponse badgesResponse = new BadgesResponse();
    	badgesResponse.setBadges(badges);
    	
    	return badgesResponse;
    }
    
    @GetMapping("/children/{username}/missions/{missionId}")
    public MissionResponse childMission(@PathVariable("username") String username, @PathVariable("missionId") String id, HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false);
    	
    	SessionResponse session1 = new SessionResponse();
    	session1.setSession("session1");
    	session1.setStatus("unknown status");
    	
    	SessionResponse session2 = new SessionResponse();
    	session2.setSession("session2");
    	session2.setStatus("unknown status");
    	
    	List<SessionResponse> sessions = new ArrayList<>();
    	sessions.add(session1);
    	sessions.add(session2);
    	
    	
    	MissionResponse missionResponse = new MissionResponse();
    	missionResponse.setRating(65.2);
    	missionResponse.setStatus("unknown status");
    	missionResponse.setSessions(sessions);
    	
    	List<String> badges = new ArrayList<>();
    	badges.add("badge1");
    	badges.add("badge2");
    	badges.add("badge5");
    	
    	BadgesResponse badgesResponse = new BadgesResponse();
    	badgesResponse.setBadges(badges);
    	
    	return missionResponse;
    }
    
    @GetMapping("/children/{username}/sessions/{sessionId}/power")
    public PowerResponse childMissionPower(@PathVariable("username") String username, 
    		@PathVariable("sessionId") String sessionId, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false);
    	
    	GraphResponse graphResponse1 = new GraphResponse();
    	graphResponse1.setTimestamp(1679740020);
    	graphResponse1.setValue(0.0);
    	
    	GraphResponse graphResponse2 = new GraphResponse();
    	graphResponse2.setTimestamp(1679740050);
    	graphResponse2.setValue(991.0);
    	
    	GraphResponse graphResponse3 = new GraphResponse();
    	graphResponse3.setTimestamp(1679740080);
    	graphResponse3.setValue(1027.0);
    	
    	List<GraphResponse> graphResponses = new ArrayList<>();
    	graphResponses.add(graphResponse1);
    	graphResponses.add(graphResponse2);
    	graphResponses.add(graphResponse3);
    	
    	PowerResponse powerResponse = new PowerResponse();
    	powerResponse.setWrtTime(graphResponses);
 	
    	return powerResponse;
    }
    
    @GetMapping("/children/{username}/sessions/{sessionId}/fog-analysis")
    public FogAnalysisResponse childMissionFogAnalysis(@PathVariable("username") String username, 
    		@PathVariable("sessionId") String sessionId, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false);

    	FogAnalysisResponse fogAnalysisResponse = new FogAnalysisResponse();
    	fogAnalysisResponse.setAttention("attention");
    	fogAnalysisResponse.setDecodedMolecules(5);
    	fogAnalysisResponse.setFrozenDishes(4);
    	fogAnalysisResponse.setPerformance("performance");
 	
    	return fogAnalysisResponse;
    }
    
    @GetMapping("/children/{username}/sessions/{sessionId}/attention")
    public AttentionResponse childMissionAttention(@PathVariable("username") String username, 
    		@PathVariable("sessionId") String sessionId, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false);
    	
    	GraphResponse graphResponse1 = new GraphResponse();
    	graphResponse1.setTimestamp(1679740020);
    	graphResponse1.setValue(23.5);
    	
    	GraphResponse graphResponse2 = new GraphResponse();
    	graphResponse2.setTimestamp(1679740050);
    	graphResponse2.setValue(72.4);
    	
    	GraphResponse graphResponse3 = new GraphResponse();
    	graphResponse3.setTimestamp(1679740080);
    	graphResponse3.setValue(38.7);
    	
    	List<GraphResponse> graphResponses = new ArrayList<>();
    	graphResponses.add(graphResponse1);
    	graphResponses.add(graphResponse2);
    	graphResponses.add(graphResponse3);

    	AttentionResponse attentionResponse = new AttentionResponse();
    	attentionResponse.setAttention(graphResponses);
    	attentionResponse.setAverageAttention(43.7);
    	attentionResponse.setAverageAttentionRequired(45.9);
 	
    	return attentionResponse;
    }
    
    @GetMapping("/opensearch/completed-sessions")
    public SearchResponse completedSessions(HttpServletRequest request) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        MatchQueryBuilder matchQueryBuilder = QueryBuilders.matchQuery("user_id", "388357544");

        FilterAggregationBuilder filterAgg = AggregationBuilders.filter("event_filter",
                QueryBuilders.termsQuery("event_type", Arrays.asList("TransferenceStatsEnd", "RunnerEnd")));

        DateHistogramAggregationBuilder dateHistogramAgg = AggregationBuilders.dateHistogram("documents_per_bucket")
        	    .field("timestamp")
        	    .minDocCount(1)
        	    .fixedInterval(new DateHistogramInterval("12h")) 
        	    .subAggregation(filterAgg);

        // Build the search request
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(matchQueryBuilder)
                .aggregation(dateHistogramAgg)
                .size(0);

        SearchRequest searchRequest = new SearchRequest("gamelogs-ref");
        searchRequest.source(searchSourceBuilder);

        return opensearchService.search(sslContext, credentialsProvider, searchRequest);  	
    }
    
    @GetMapping("/opensearch/completed-sessions-week")
    public SearchResponse completedSessionsWeek(HttpServletRequest request) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        QueryBuilder matchQueryBuilder = QueryBuilders.matchQuery("user_id", "388357544");

        QueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("this_week")
            .gte("first_date")
            .lte("last_date");

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
            .must(matchQueryBuilder)
            .must(rangeQueryBuilder);

        QueryBuilders.queryStringQuery(boolQueryBuilder.toString());

        FilterAggregationBuilder eventFilterAgg = AggregationBuilders.filter("event_filter", QueryBuilders.termsQuery("event_type", "TransferenceStatsEnd", "RunnerEnd"));

        DateHistogramAggregationBuilder dateHistogramAgg = AggregationBuilders.dateHistogram("documents_per_bucket")
            .field("timestamp")
            .minDocCount(1)
            .fixedInterval(new DateHistogramInterval("12h"))
            .subAggregation(eventFilterAgg);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);
        searchSourceBuilder.aggregation(dateHistogramAgg);
        searchSourceBuilder.size(0);

        SearchRequest searchRequest = new SearchRequest("gamelogs-ref");
        searchRequest.source(searchSourceBuilder);

        return opensearchService.search(sslContext, credentialsProvider, searchRequest);
    }
    
    @GetMapping("/opensearch/completed-missions")
    public SearchResponse completedMissions(HttpServletRequest request) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
     // Build the match queries
        QueryBuilder eventTypeQuery = QueryBuilders.matchQuery("event_type", "TransferenceStatsEnd");
        QueryBuilder userIdQuery = QueryBuilders.matchQuery("user_id", "388357544");

        // Combine the match queries into a boolean query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(eventTypeQuery)
                .must(userIdQuery);

        // Build the aggregation query
        TermsAggregationBuilder missionsAgg = AggregationBuilders.terms("missions")
                .field("MissionID")
                .size(15);

        // Build the search source with the boolean query, the aggregation, and the size
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(boolQuery)
                .aggregation(missionsAgg)
                .size(0)
                .fetchSource(false);

        // Build the search request
        SearchRequest searchRequest = new SearchRequest("gamelogs-ref")
                .source(searchSourceBuilder);

        return opensearchService.search(sslContext, credentialsProvider, searchRequest);  	
    }
    
    @GetMapping("/opensearch/power")
    public SearchResponse power(HttpServletRequest request) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
     // Build the match queries
        QueryBuilder sessionStartQuery = QueryBuilders.matchQuery("session_start.keyword", "2023-03-28 13:37:07.845");
        QueryBuilder sessionTypeQuery = QueryBuilders.matchQuery("session_type", "runner");

        // Combine the match queries into a boolean query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(sessionStartQuery)
                .must(sessionTypeQuery);

        // Build the aggregation queries
        MaxAggregationBuilder powerAgg = AggregationBuilders.max("power").field("Score");
        DateHistogramAggregationBuilder intervalsAgg = AggregationBuilders.dateHistogram("intervals")
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
        SearchRequest searchRequest = new SearchRequest("gamelogs-ref")
                .source(searchSourceBuilder);

        return opensearchService.search(sslContext, credentialsProvider, searchRequest);  	
    }
    
    @GetMapping("/opensearch/attention")
    public SearchResponse attention(HttpServletRequest request) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        QueryBuilder sessionStartQuery = QueryBuilders.matchQuery("session_start.keyword", "2023-03-25 10:27:25.344");
        QueryBuilder sessionTypeQuery = QueryBuilders.matchQuery("session_type", "runner");

        // Combine the match queries into a boolean query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(sessionStartQuery)
                .must(sessionTypeQuery);

        // Build the aggregation queries
        AvgAggregationBuilder averageBciAgg = AggregationBuilders.avg("average_bci").field("bci");
        DateHistogramAggregationBuilder intervalsAgg = AggregationBuilders.dateHistogram("intervals")
                .field("timestamp")
                .fixedInterval(DateHistogramInterval.seconds(60))
                .subAggregation(averageBciAgg);

        // Build the search source with the boolean query, the aggregation, and the size
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(boolQuery)
                .aggregation(intervalsAgg)
                .size(0)
                .fetchSource(false);

        // Build the search request
        SearchRequest searchRequest = new SearchRequest("gamelogs-ref")
                .source(searchSourceBuilder);

        return opensearchService.search(sslContext, credentialsProvider, searchRequest);
    }
    
    
    @GetMapping("/me")
    public Parent getParent(HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false);
    	return mongoService.getParent(jwt.getClaim("cognito:username"));		
    }

}