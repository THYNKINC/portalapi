package com.portal.api.controllers;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.core.CountRequest;
import org.opensearch.client.core.CountResponse;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.opensearch.search.aggregations.bucket.histogram.Histogram;
import org.opensearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.opensearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.opensearch.search.aggregations.metrics.AvgAggregationBuilder;
import org.opensearch.search.aggregations.metrics.Max;
import org.opensearch.search.aggregations.metrics.MaxAggregationBuilder;
import org.opensearch.search.aggregations.metrics.ParsedAvg;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.api.exception.ResourceNotFoundException;
import com.portal.api.model.AttentionResponse;
import com.portal.api.model.Badge;
import com.portal.api.model.BadgesResponse;
import com.portal.api.model.Child;
import com.portal.api.model.CreateChildRequest;
import com.portal.api.model.CreateParentRequest;
import com.portal.api.model.CreateUserRequest;
import com.portal.api.model.CustomSearchResponse;
import com.portal.api.model.FogAnalysisResponse;
import com.portal.api.model.GameState;
import com.portal.api.model.GraphResponse;
import com.portal.api.model.LoginRequest;
import com.portal.api.model.Parent;
import com.portal.api.model.PowerResponse;
import com.portal.api.model.ProgressResponse;
import com.portal.api.model.RecentMissionResponse;
import com.portal.api.model.SessionData;
import com.portal.api.model.StartEnd;
import com.portal.api.util.HttpService;
import com.portal.api.util.JwtService;
import com.portal.api.util.MappingService;
import com.portal.api.util.MongoService;
import com.portal.api.util.OpensearchService;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
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
    
    @GetMapping("/me")
    public Parent getParent(HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, null);
    	return mongoService.getParent(jwt.getClaim("cognito:username"));		
    }
    
    @PostMapping("/login")
    public String login(@RequestBody @Valid LoginRequest loginRequest, HttpServletRequest request) throws Exception {
    	return loginInternal(loginRequest);
    }
    
    public String loginInternal(LoginRequest loginRequest) throws Exception {
    	
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
    public void createParent(@Valid @RequestBody CreateParentRequest createParentRequest, HttpServletRequest request) throws Exception {
    	
    	// Create a CognitoIdentityProviderClient
    	CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

    	SignUpRequest signUpRequest = SignUpRequest.builder()
    	        .clientId(APP_CLIENT_ID)
    	        .username(createParentRequest.getEmail())
    	        .password(createParentRequest.getPassword())
    	        .userAttributes(
    	                AttributeType.builder().name("email").value(createParentRequest.getEmail()).build(),
    	                AttributeType.builder().name("family_name").value(createParentRequest.getLastName()).build(),
    	                AttributeType.builder().name("given_name").value(createParentRequest.getFirstName()).build()
    	        )
    	        .build();
    	
    	// Call the signUp method to create the user
    	SignUpResponse signUpResponse = cognitoClient.signUp(signUpRequest);

    	// Access the user's username and other details from the signUpResponse
    	String usern = signUpResponse.userSub();
    	System.out.println("User created with username sub: " + usern);
    	
    	// Add user to user group
    	AdminAddUserToGroupRequest addUserToGroupRequest = AdminAddUserToGroupRequest.builder()
    	        .userPoolId(USER_POOL_ID)
    	        .username(createParentRequest.getEmail())
    	        .groupName(GROUP_NAME_USER)
    	        .build();
    	
    	cognitoClient.adminAddUserToGroup(addUserToGroupRequest);
    	
    	AdminConfirmSignUpRequest confirmSignUpRequest = AdminConfirmSignUpRequest.builder()
    	        .userPoolId(USER_POOL_ID)
    	        .username(createParentRequest.getEmail())
    	        .build();
    	
    	AdminConfirmSignUpResponse confirmSignUpResponse = cognitoClient.adminConfirmSignUp(confirmSignUpRequest);

    	boolean isConfirmed = confirmSignUpResponse.sdkHttpResponse().isSuccessful();
    	System.out.println(isConfirmed);
    	
    	Parent parent = new Parent();
    	parent.setChildren(new ArrayList<>());
    	parent.setEmail(createParentRequest.getEmail());
    	parent.setFirstName(createParentRequest.getFirstName());
    	parent.setLastName(createParentRequest.getLastName());
    	parent.setUsername(signUpResponse.userSub());
    	
    	mongoService.upsertParent(parent);
    }
    
    @GetMapping("/children")
    public List<Child> getChildren(HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, null);
    	
    	Parent parent = mongoService.getParent(jwt.getClaim("cognito:username"));
    	return parent.getChildren();
    }

    @PostMapping("/children")
    public void createChild(@Valid @RequestBody CreateChildRequest createChildRequest, HttpServletRequest request) throws Exception {
        
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, null);
    	
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
    public ProgressResponse childProgress(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	SearchResponse searchResponse;
    	Aggregations aggregations;
    	ParsedDateHistogram terms;
    	List<? extends Histogram.Bucket> buckets;

    	System.out.println("SESSIONS WEEKLY");
    	searchResponse = completedSessionsWeekInternal(username); 
    	
    	aggregations = searchResponse.getAggregations();
    	terms = aggregations.get("documents_per_bucket"); // Get the aggregation
    	buckets = terms.getBuckets();
    	int sessionsPerWeekCount = buckets.size();
    	
    	System.out.println("SESSIONS");
    	searchResponse = completedSessionsInternal(username); 

    	aggregations = searchResponse.getAggregations();
    	terms = aggregations.get("documents_per_bucket"); // Get the aggregation
    	buckets = terms.getBuckets();
    	int sessionsCount = buckets.size();

    	System.out.println("MISSIONS");
    	searchResponse = completedMissionsInternal(username); 
    	
    	Aggregation aggregation = searchResponse.getAggregations().get("missions");
	    ParsedStringTerms stringTerms = (ParsedStringTerms) aggregation;
	    int missionsCount = (int) stringTerms.getBuckets().size();
    	
    	ProgressResponse progressResponse = new ProgressResponse();
    	progressResponse.setAbandonedAttempts(0);
    	progressResponse.setSessionsCompletedPerWeek(sessionsPerWeekCount);
    	progressResponse.setMissionsCompleted(missionsCount);
    	progressResponse.setSessionsCompleted(sessionsCount);
    	
    	return progressResponse;
    }
    
    @GetMapping("/children/{username}/recent-mission")
    public RecentMissionResponse childRecentMission(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	// TODO change to use Spring web request, which includes JWT exchange
        String bearerToken = jwtService.getAdminJwt();
        
        // TODO change to bean
        // TODO change to use Spring web request
        String url = String.format("http://%s:%s/games/users/%s/game-state", GAMES_SERVICE, GAMES_PORT, username);
        String result = HttpService.sendHttpGetRequest(url, bearerToken);
        
        GameState state;
        try {
        	ObjectMapper mapper = new ObjectMapper();
        	state = mapper.readValue(result, GameState.class);
        } catch (JsonMappingException e) {
    	   throw new ResourceNotFoundException("Resource not found");
    	}
    	
        SearchResponse searchResponse = lastSessionInternal(username); 
    	
    	Map<String, Object> sourceAsMap = searchResponse.getHits().getHits()[0].getSourceAsMap();
    	
//    	String sessionId = (String) sourceAsMap.get("session_start");
//    	String eventType = (String) sourceAsMap.get("event_type");
    	String taskId = (String) sourceAsMap.get("TaskID");
    	int lastCompleted = Integer.parseInt(MappingService.getKey(taskId));
    	
    	RecentMissionResponse recentMissionResponse = new RecentMissionResponse();
    	
    	// 5 levels, 3 sublevels per level, that's 15 missions total
    	recentMissionResponse.setMissionNumber(lastCompleted);
    	
		// starsPerMission is a string where each character is a number representing the stars earned for the mission at that index (zero-based hence the -1)
		recentMissionResponse.setMissionRating(Character.getNumericValue(state.getStarsPerMission().charAt(lastCompleted - 1)));
    	
		recentMissionResponse.setMissionStatus(recentMissionResponse.getMissionRating() > 0 ? "PASS" : "FAIL");
    	
    	return recentMissionResponse;
    }
    
    @GetMapping("/children/{username}/badges")
    public BadgesResponse childBadges(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	//TODO this is a mock, we need to get this from Mongo
    	List<Badge> badges = new ArrayList<>();
    	for (int i = 0; i<= 3; i++) {
    		Badge badge = new Badge();
    		badge.setDescription("description" + i);
    		badge.setImageUrl("url" + i);
    		badge.setName("badgename" + i);
    		badges.add(badge);
    	}
    	
    	BadgesResponse badgesResponse = new BadgesResponse();
    	badgesResponse.setBadges(badges);
    	
    	return badgesResponse;
    }
    
    @GetMapping("/children/{username}/missions/{missionId}")
    public List<SessionData> childMission(@PathVariable("username") String username, @PathVariable("missionId") String missionId, HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	String convertedMissionId = MappingService.getValue(missionId);
    	
    	SearchResponse searchResponse = missionsInternal(username, convertedMissionId); 
    	
    	List<SessionData> sessionDataList = new ArrayList<>();

    	for (SearchHit hit : searchResponse.getHits().getHits()) {
    	    Map<String, Object> sourceAsMap = hit.getSourceAsMap();

    	    String sessionId = (String) sourceAsMap.get("session_start");
    	    String eventType = (String) sourceAsMap.get("event_type");

    	    SessionData sessionData = new SessionData();
    	    sessionData.setSessionId(sessionId);
    	    sessionData.setEventType(eventType);

    	    sessionDataList.add(sessionData);
    	}
    	
    	return sessionDataList;
    }
    
    @GetMapping("/children/{username}/sessions/{sessionId}/power")
    public PowerResponse childMissionPower(@PathVariable("username") String username, 
    		@PathVariable("sessionId") String sessionId, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	SearchResponse searchResponse = powerInternal(username, sessionId); 
    	
    	Aggregations aggregations = searchResponse.getAggregations();

    	Histogram interval = aggregations.get("intervals");
    	List<GraphResponse> graphResponseList = new ArrayList<>();

    	for (Histogram.Bucket entry : interval.getBuckets()) {
    		ZonedDateTime keyAsZonedDateTime = (ZonedDateTime) entry.getKey(); // This gets the key as a ZonedDateTime object.
    	    Long key = keyAsZonedDateTime.toInstant().toEpochMilli(); // This converts the ZonedDateTime to a timestamp (Long).   	   
    	    
    	    Max max = entry.getAggregations().get("power");
    	    Double value = max.getValue();
    	    
    	    if(value != null && !Double.isNaN(value) && !Double.isInfinite(value)) {
    	    	GraphResponse graphResponse = new GraphResponse();
    	    	graphResponse.setTimestamp(key);
    	    	value = (value / 19808) * 100;
    	    	graphResponse.setValue(value);
    	    
    	        graphResponseList.add(graphResponse);
    	    }
    	}
    	
    	searchResponse = starValuesInternal(username, sessionId);
    	
    	Map<String, Object> sourceAsMap = searchResponse.getHits().getHits()[0].getSourceAsMap();
    	
//    	String sessionId = (String) sourceAsMap.get("session_start");
//    	String eventType = (String) sourceAsMap.get("event_type");
    	String starValues = (String) sourceAsMap.get("StarValues");
    	String[] percentages = starValues.split("&");
    	
    	int values[] = new int[3];
    	
    	for (int i = 0; i < 3; i++) {
    		values[i] = Math.round(Float.parseFloat(percentages[i]) * 19808);
    	}
    	
    	PowerResponse response = new PowerResponse();
    	response.setData(graphResponseList);
    	response.setThresholds(values);
    	
    	return response;
    }
    
    @GetMapping("/children/{username}/sessions/{sessionId}/fog-analysis")
    public FogAnalysisResponse childMissionFogAnalysis(@PathVariable("username") String username, 
    		@PathVariable("sessionId") String sessionId, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	CountResponse countResponse = decodedMoleculesInternal(username, sessionId);
    	CustomSearchResponse customSearchResponse = frozenDishesInternal(username, sessionId);
    	SearchResponse searchResponse = transferenceEventsInternal(username, sessionId);
    	
    	Map<String, Long> dishCounts = customSearchResponse.getDishCount();
    	long dishStartCount = dishCounts.getOrDefault("TransferenceStatsDishStart", 0L);
    	long dishCorruptedCount = dishCounts.getOrDefault("TransferenceStatsDishCorrupted", 0L);
    	int difference = (int) (dishStartCount - dishCorruptedCount);
    	
    	List<StartEnd> startEndList = new ArrayList<>();
    	SearchHit[] searchHits = searchResponse.getHits().getHits();
    	for (int i = 0; i < searchHits.length - 1; i += 2) {
    	    String startTimestamp = (String) searchHits[i].getSourceAsMap().get("timestamp");
    	    String endTimestamp = (String) searchHits[i + 1].getSourceAsMap().get("timestamp");
    	    startEndList.add(new StartEnd(startTimestamp, endTimestamp));
    	}

    	FogAnalysisResponse fogAnalysisResponse = new FogAnalysisResponse();
    	fogAnalysisResponse.setDecodedMolecules((int) countResponse.getCount());
    	fogAnalysisResponse.setFrozenDishes(difference);
    	fogAnalysisResponse.setDishes(startEndList);
 	
    	return fogAnalysisResponse;
    }
    
    @GetMapping("/children/{username}/sessions/{sessionId}/attention")
    public AttentionResponse childMissionAttention(@PathVariable("username") String username, 
    		@PathVariable("sessionId") String sessionId, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	SearchResponse searchResponse = attentionInternal(username, sessionId); 
    	
    	Aggregations aggregations = searchResponse.getAggregations();

    	Histogram interval = aggregations.get("intervals");
    	List<GraphResponse> graphResponseList = new ArrayList<>();

    	int count = 0;
    	Double totalValue = 0.0;
    	for (Histogram.Bucket entry : interval.getBuckets()) {
    		ZonedDateTime keyAsZonedDateTime = (ZonedDateTime) entry.getKey(); // This gets the key as a ZonedDateTime object.
    	    Long key = keyAsZonedDateTime.toInstant().toEpochMilli(); // This converts the ZonedDateTime to a timestamp (Long).   	   
    	    
    	    ParsedAvg avg = entry.getAggregations().get("average_bci");
    	    Double value = avg.getValue();
    	    
    	    if(value != null && !Double.isNaN(value) && !Double.isInfinite(value)) {
    	    	GraphResponse graphResponse = new GraphResponse();
    	    	graphResponse.setTimestamp(key);
    	    	graphResponse.setValue(value);
    	    
    	        graphResponseList.add(graphResponse);
    	        
    	        totalValue = totalValue + value;
    	        count++;
    	    }
    	}

    	AttentionResponse attentionResponse = new AttentionResponse();
    	attentionResponse.setAttention(graphResponseList);
    	attentionResponse.setAverageAttention((totalValue / count));
    	attentionResponse.setAverageAttentionRequired(0.0);
 	
    	return attentionResponse;
    }
    
    @GetMapping("/opensearch/completed-sessions")
    public SearchResponse completedSessions(HttpServletRequest request) throws Exception {
    	return completedSessionsInternal("388357544");
    }
    
    public SearchResponse completedSessionsInternal(String userId) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        MatchQueryBuilder matchQueryBuilder = QueryBuilders.matchQuery("user_id", userId);

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
    	return completedSessionsWeekInternal("388357544");
    }
    
    public SearchResponse completedSessionsWeekInternal(String userId) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        QueryBuilder matchQueryBuilder = QueryBuilders.matchQuery("user_id", userId);

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
    	return completedMissionsInternal("1942396312");
    }
    
    public SearchResponse completedMissionsInternal(String userId) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
     // Build the match queries
        QueryBuilder eventTypeQuery = QueryBuilders.matchQuery("event_type", "TransferenceStatsEnd");
        QueryBuilder userIdQuery = QueryBuilders.matchQuery("user_id", userId);

        // Combine the match queries into a boolean query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(eventTypeQuery)
                .must(userIdQuery);

        // Build the aggregation query
        TermsAggregationBuilder missionsAgg = AggregationBuilders.terms("missions")
                .field("TaskID")
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

    
    @GetMapping("/opensearch/missions")
    public SearchResponse missions(String userId, String missionId) throws Exception {
    	return missionsInternal("388357544", "5.2"); 
    }
    
    public SearchResponse missionsInternal(String userId, String missionId) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        SearchRequest searchRequest = new SearchRequest("gamelogs-ref");

     // Create queries
     BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
             .must(QueryBuilders.termQuery("TaskID", missionId))
             .must(QueryBuilders.termsQuery("event_type", "RunnerEnd", "TransferenceStatsEnd"))
             .must(QueryBuilders.matchQuery("user_id", userId));

     // Set up the source builder
     SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
     searchSourceBuilder.query(boolQuery);
     searchSourceBuilder.size(20);
     searchSourceBuilder.sort("timestamp", SortOrder.DESC);

     // Add the fields to the request
     searchSourceBuilder.fetchSource(new String[] { "session_start", "event_type" }, new String[] {});

     // Add the source builder to the request
     searchRequest.source(searchSourceBuilder);

        return opensearchService.search(sslContext, credentialsProvider, searchRequest);  	
    }
    
    public SearchResponse lastSessionInternal(String userId) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        SearchRequest searchRequest = new SearchRequest("gamelogs-ref");

	     // Create queries
	     BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
	             .must(QueryBuilders.termsQuery("event_type", "RunnerEnd", "TransferenceStatsEnd"))
	             .must(QueryBuilders.matchQuery("user_id", userId));
	
	     // Set up the source builder
	     SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
	     searchSourceBuilder.query(boolQuery);
	     searchSourceBuilder.size(20);
	     searchSourceBuilder.sort("timestamp", SortOrder.DESC);
	
	     // Add the fields to the request
	     searchSourceBuilder.fetchSource(new String[] { "session_start", "event_type", "TaskID" }, new String[] {});
	
	     // Add the source builder to the request
	     searchRequest.source(searchSourceBuilder);

        return opensearchService.search(sslContext, credentialsProvider, searchRequest);  	
    }
    
    @GetMapping("/opensearch/power")
    public SearchResponse power(HttpServletRequest request) throws Exception {
    	return powerInternal("388357544", "2023-04-24 16:28:16.566");
    }
    	
    public SearchResponse powerInternal(String userId, String sessionId) throws Exception {
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        // Build the match queries
        QueryBuilder sessionStartQuery = QueryBuilders.matchQuery("session_start.keyword", sessionId);
        QueryBuilder sessionTypeQuery = QueryBuilders.matchQuery("session_type", "runner");
        QueryBuilder userIdQuery = QueryBuilders.matchQuery("user_id", userId);

        // Combine the match queries into a boolean query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(sessionStartQuery)
                .must(sessionTypeQuery)
                .must(userIdQuery);

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
    
    public SearchResponse starValuesInternal(String userId, String sessionId) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        // Build the match queries
        QueryBuilder sessionStartQuery = QueryBuilders.matchQuery("session_start.keyword", sessionId);
        QueryBuilder eventTypeQuery = QueryBuilders.matchQuery("event_type", "RunnerStart");
        QueryBuilder userIdQuery = QueryBuilders.matchQuery("user_id", userId);

        // Combine the match queries into a boolean query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(sessionStartQuery)
                .must(eventTypeQuery)
                .must(userIdQuery);

        // Build the search source with the boolean query, and the size
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(boolQuery)
                .size(1)
                .fetchSource(new String[] { "StarValues" }, new String[] {});;

        // Build the search request
        SearchRequest searchRequest = new SearchRequest("gamelogs-ref")
                .source(searchSourceBuilder);

        return opensearchService.search(sslContext, credentialsProvider, searchRequest);  	
    }
    
    @GetMapping("/opensearch/attention")
    public SearchResponse attention(HttpServletRequest request) throws Exception {
    	return attentionInternal("388357544", "2023-04-24 16:28:16.566");
    }
    
    
    public SearchResponse attentionInternal(String userId, String sessionId) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        QueryBuilder sessionStartQuery = QueryBuilders.matchQuery("session_start.keyword", sessionId);
        QueryBuilder userIdQuery = QueryBuilders.matchQuery("user_id", userId);
        
     // Combine the match queries into a boolean query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(sessionStartQuery)
                .must(userIdQuery);
        
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
    
    @GetMapping("/opensearch/frozen-dishes")
    public CustomSearchResponse frozenDishes(HttpServletRequest request) throws Exception {
    	return frozenDishesInternal("388357544", "2023-03-04 06:30:23.06");
    }
    
    
    public CustomSearchResponse frozenDishesInternal(String userId, String sessionId) throws Exception {
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
        	    .must(QueryBuilders.termQuery("session_start.keyword", sessionId))
        	    .must(QueryBuilders.termsQuery("event_type", "TransferenceStatsDishStart", "TransferenceStatsDishCorrupted"))
        	    .must(QueryBuilders.matchQuery("user_id", userId));

    	// Build the aggregation
    	TermsAggregationBuilder aggregation = AggregationBuilders.terms("dish_count").field("event_type");

    	// Build the search source
    	SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
    	    .query(boolQuery)
    	    .aggregation(aggregation)
    	    .size(0);

    	// Create the search request
    	SearchRequest searchRequest = new SearchRequest("gamelogs-ref")
    	    .source(searchSourceBuilder);
    	
    	SearchResponse searchResponse = opensearchService.search(sslContext, credentialsProvider, searchRequest); 
    	
    	Terms dishCountAggregation = searchResponse.getAggregations().get("dish_count");
    	Map<String, Long> dishCount = dishCountAggregation.getBuckets().stream()
    	    .collect(Collectors.toMap(Terms.Bucket::getKeyAsString, Terms.Bucket::getDocCount));

    	CustomSearchResponse customResponse = new CustomSearchResponse(dishCount);

    	return customResponse;
    
    }
    
    @GetMapping("/opensearch/decoded-molecules")
    public CountResponse decodedMolecules(HttpServletRequest request) throws Exception {
    	return decodedMoleculesInternal("388357544", "2023-02-25 10:15:39.971");
    }
    
    
    public CountResponse decodedMoleculesInternal(String userId, String sessionId) throws Exception {
    	
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
        	    .must(new MatchQueryBuilder("session_start.keyword", sessionId))
        	    .must(new TermQueryBuilder("event_type", "TransferenceStatsMoleculeDecodeEnd"))
        	    .must(QueryBuilders.matchQuery("user_id", userId));

    	CountRequest countRequest = new CountRequest("gamelogs-ref");
    	countRequest.query(boolQueryBuilder);
        
        return opensearchService.count(sslContext, credentialsProvider, countRequest); 
    }
    
    @GetMapping("/opensearch/transference-events")
    public SearchResponse transferenceEvents(HttpServletRequest request) throws Exception {
    	return transferenceEventsInternal("388357544", "2023-03-26 16:04:33.386");
    }
    
    
    public SearchResponse transferenceEventsInternal(String userId, String sessionId) throws Exception {
    	SSLContext sslContext = opensearchService.getSSLContext();
        BasicCredentialsProvider credentialsProvider = opensearchService.getBasicCredentialsProvider();
        
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
        	    .must(QueryBuilders.termQuery("session_start.keyword", sessionId))
        	    .must(QueryBuilders.termsQuery("event_type", "TransferenceStatsDishStart", "TransferenceStatsDishEnd"))
        	    .must(QueryBuilders.matchQuery("user_id", userId));

    	// Specify the fields to return
    	String[] includeFields = new String[] {"timestamp", "event_type"};
    	String[] excludeFields = new String[] {};
    	SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
    	    .query(boolQuery)
    	    .fetchSource(includeFields, excludeFields)
    	    .size(20)
    	    .sort("timestamp", SortOrder.ASC);

    	// Create the search request
    	SearchRequest searchRequest = new SearchRequest("gamelogs-ref")
    	    .source(searchSourceBuilder);
        
        return opensearchService.search(sslContext, credentialsProvider, searchRequest); 
    	
    }
   
    @GetMapping("/test")
    public String teting(HttpServletRequest request) throws Exception {
    	return "test 1";
    }
   

}