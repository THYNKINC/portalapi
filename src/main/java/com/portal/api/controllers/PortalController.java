package com.portal.api.controllers;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import com.portal.api.services.*;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.core.CountResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.filter.Filter;
import org.opensearch.search.aggregations.bucket.histogram.Histogram;
import org.opensearch.search.aggregations.metrics.Max;
import org.opensearch.search.aggregations.metrics.ParsedAvg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.api.dto.response.AttentionResponse;
import com.portal.api.model.Badge;
import com.portal.api.dto.response.BadgesResponse;
import com.portal.api.model.Child;
import com.portal.api.dto.response.CognitiveSkillsProgressResponse;
import com.portal.api.dto.response.CognitiveSkillsResponse;
import com.portal.api.dto.request.CreateChildRequest;
import com.portal.api.dto.request.CreateParentRequest;
import com.portal.api.dto.request.CreateUserRequest;
import com.portal.api.dto.response.CustomSearchResponse;
import com.portal.api.dto.response.FogAnalysisResponse;
import com.portal.api.dto.response.GraphResponse;
import com.portal.api.model.HistoricalProgressReport;
import com.portal.api.model.ImpulseControl;
import com.portal.api.dto.request.LoginRequest;
import com.portal.api.model.Parent;
import com.portal.api.model.PortalUser;
import com.portal.api.dto.response.PowerResponse;
import com.portal.api.dto.response.ProgressResponse;
import com.portal.api.dto.response.RecentMissionResponse;
import com.portal.api.model.Role;
import com.portal.api.dto.response.RunnerResponse;
import com.portal.api.model.RunnerSummary;
import com.portal.api.model.SessionData;
import com.portal.api.model.SessionSummary;
import com.portal.api.model.SkillItem;
import com.portal.api.model.StartEnd;
import com.portal.api.model.TransferenceSummary;
import com.portal.api.util.HttpService;
import com.portal.api.util.JwtService;
import com.portal.api.util.MappingService;

import io.swagger.v3.oas.annotations.Hidden;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
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

	@Value("${games-port}")
	private String GAMES_PORT;

	@Value("${games-service}")
	private String GAMES_SERVICE;
	
	@Value("${assets-baseurl}")
	private String assetsBaseUrl;
	
	private final JwtService jwtService;
	
	private final ParentService parentService;
	
	private final AnalyticsService analyticsService;

    private final GameApiService gameApiService;

	private final AuthService authService;

    @Autowired
    public PortalController(
            JwtService jwtService,
            ParentService mongoService,
            AnalyticsService analyticsService, GameApiService gameApiService, AuthService authService) {
        this.jwtService = jwtService;
        this.parentService = mongoService;
        this.analyticsService = analyticsService;
        this.gameApiService = gameApiService;
        this.authService = authService;
    }
    
    @GetMapping("/me")
    public PortalUser getMe(HttpServletRequest request) throws Exception {
    	
    	return jwtService.decodeJwtFromRequest(request, false, null);
    }
    
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody @Valid LoginRequest loginRequest) {
    	return ResponseEntity.ok(authService.login(loginRequest));
    }

    @PostMapping("/signup")
    public void createParent(@Valid @RequestBody CreateParentRequest createParentRequest) {
		parentService.createParent(createParentRequest);
    }
    
    @GetMapping("/children")
    public List<Child> getChildren(HttpServletRequest request) throws Exception {
    	PortalUser user = jwtService.decodeJwtFromRequest(request, false, null);
    	return user.getChildren();
    }

    @PostMapping("/children")
    public void createChild(@Valid @RequestBody CreateChildRequest createChildRequest, HttpServletRequest request) throws Exception {
        
    	PortalUser user = jwtService.decodeJwtFromRequest(request, false, null);
    	
    	if (user.getRole() != Role.parent) {
    		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only parents can create children");
    	}

		gameApiService.createNewUserFromChild(createChildRequest, user, jwtService.getAdminJwt());
		parentService.addChildToParent(createChildRequest, user.getUsername());
    }

    @GetMapping("/children/{username}/progress")
    public ProgressResponse childProgress(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	jwtService.decodeJwtFromRequest(request, false, username);
    	
    	SearchResponse response = analyticsService.historicalProgress(username);
    	HistoricalProgressReport progressReport = HistoricalProgressReport.parse(response);
    	
    	SearchResponse searchResponse;
    	Aggregations aggregations;
    	    	
    	ProgressResponse progressResponse = new ProgressResponse();
    	
    	progressResponse.setSessionsCompleted(progressReport.getSessionsCompleted());
    	
    	searchResponse = analyticsService.weeklyStats(username); 
    	
    	aggregations = searchResponse.getAggregations();
    	
    	int starts = (int)searchResponse.getHits().getTotalHits().value;
    	
    	Filter attempts = aggregations.get("attempts");
    	int attemptsCount = (int)attempts.getDocCount();
    	
    	Histogram sessions = attempts.getAggregations().get("sessions");
    	int sessionsPerWeekCount = sessions.getBuckets().size();
    	progressResponse.setSessionsCompletedPerWeek(sessionsPerWeekCount);
 
    	progressResponse.setAbandonedAttempts(starts - attemptsCount);
    	progressResponse.setMissionsCompleted(progressReport.getHighestMission());
		
		// get the last attempt (could be different from highest mission if they went back to an old mission)
		searchResponse = analyticsService.lastNRunners(username, 1);
		
		RunnerSummary runner = (RunnerSummary)SearchResultsMapper.getSession(searchResponse.getHits().getHits()[0]);
		
		// calculate thynk score
		double thynkScore = (1.7 * progressReport.getHighestMission() + progressReport.getTotalAttempts())
				* (runner.getScores().getCompositeFocus()/ 100 + 1);
		
    	progressResponse.setThynkScore((int)Math.ceil(thynkScore));
    	
    	return progressResponse;
    }
    
    @GetMapping("/children/{username}/recent-mission")
    public RecentMissionResponse childRecentMission(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	jwtService.decodeJwtFromRequest(request, false, username);
    	
        SearchResponse searchResponse = analyticsService.lastAttempt(username); 
        
        if (searchResponse.getHits().getHits().length == 0)
        	return new RecentMissionResponse();
        
        SessionSummary session = SearchResultsMapper.getSession(searchResponse.getHits().getHits()[0]);
    	
    	RecentMissionResponse recentMissionResponse = new RecentMissionResponse();
    	
    	recentMissionResponse.setMissionNumber(session.getMissionId());
    	recentMissionResponse.setSessionId(session.getId());
    	
    	if (session instanceof RunnerSummary) {
    		
    		RunnerSummary runner = (RunnerSummary)session;
    		
    		recentMissionResponse.setMissionStatus(runner.getStatus());
    		recentMissionResponse.setMissionRating(runner.getStars().size());
    		recentMissionResponse.setType("runner");
    	}
    	
    	else if (session instanceof TransferenceSummary) {
    		
    		TransferenceSummary xfer = (TransferenceSummary)session;
    		
    		recentMissionResponse.setMissionStatus(session.getStatus());
    		recentMissionResponse.setMissionRating(xfer.getDecoded() * 100 / xfer.getTarget());
    		recentMissionResponse.setType("transference");
    	}
    	
    	else {
    		
    		recentMissionResponse.setMissionStatus("PASS");
    		recentMissionResponse.setMissionRating(100);
    		recentMissionResponse.setType("vigilock");
    	}
    	
    	return recentMissionResponse;
    }
    
    @GetMapping("/children/{username}/sessions/{sessionId}/runner")
    public RunnerResponse childMissionRunner(
    		@PathVariable("username") String username, 
    		@PathVariable("sessionId") String sessionId,
    		HttpServletRequest request) throws Exception {
    	
    	PortalUser user = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	SearchResponse searchResponse = analyticsService.maxStarReached(username, sessionId);
    	
    	RunnerResponse runner = new RunnerResponse();
    	
    	SearchHit[] searchHits = searchResponse.getHits().getHits();
    	
    	if (searchHits.length == 0)
    		return runner;
    	
    	int starReached = (int)searchHits[0].getSourceAsMap().get("StarReached");
    	
    	runner.setStarReached(starReached);
    	runner.setPass(starReached > 0);
 	
    	return runner;
    }
    
    @GetMapping("/children/{username}/badges")
    public BadgesResponse childBadges(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	PortalUser user = jwtService.decodeJwtFromRequest(request, false, username);

    	String bearerToken = jwtService.getAdminJwt();
        String response = HttpService.sendHttpGetRequest("http://" + GAMES_SERVICE + ":" + GAMES_PORT + "/games/users/" + username + "/game-state", bearerToken);
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);
        
        List<Badge> badges = new ArrayList<>();
        
        root.fields().forEachRemaining(field -> {
        	
        	if (field.getKey().startsWith("ach") && field.getValue().asInt() == 1) {
        		String badgeName = field.getKey().substring(4);
        		badges.add(new Badge(badgeName, assetsBaseUrl + "badges/" + badgeName + "_1024.png", "some description"));
        	}
        });
        
        BadgesResponse badgesResponse = new BadgesResponse();
    	badgesResponse.setBadges(badges);
    	
    	return badgesResponse;
    }
    
    @GetMapping("/children/{username}/missions/{missionId}")
    public List<SessionData> childMission(@PathVariable("username") String username, @PathVariable("missionId") String missionId, HttpServletRequest request) throws Exception {
    	PortalUser user = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	String convertedMissionId = MappingService.getValue(missionId);
    	
    	SearchResponse searchResponse = analyticsService.attemptsPerMission(username, convertedMissionId); 
    	
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
    public PowerResponse childMissionPower(
    		@PathVariable("username") String username, 
    		@PathVariable("sessionId") String sessionId,
    		// TODO use Spring Boot auth Principal injection here (see legacy API)
    		HttpServletRequest request) throws Exception {
    	
    	PortalUser user = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	SearchResponse searchResponse = analyticsService.power(username, sessionId); 
    	
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
    	
    	searchResponse = analyticsService.starValues(username, sessionId);
    	
    	// this indicates a problem with the session_start for RunnerStart not matching the RunnerEnd
    	if (searchResponse.getHits().getHits().length == 0) {
    		throw new RuntimeException("Cannot determine start thresholds for " + username + ", attempt " + sessionId);
    	}
    	
    	Map<String, Object> sourceAsMap = searchResponse.getHits().getHits()[0].getSourceAsMap();
    	
//    	String sessionId = (String) sourceAsMap.get("session_start");
//    	String eventType = (String) sourceAsMap.get("event_type");
    	String starValues = (String) sourceAsMap.get("StarValues");
    	String[] thresholds = starValues.split("&");
    	
    	// special case for legacy data having only one star threshold
    	if (thresholds.length == 1) {
    		thresholds = new String[] {thresholds[0], thresholds[0], thresholds[0]};
    	}
    	
    	int values[] = new int[3];
    	int percentages[] = new int[3];
    	
    	for (int i = 0; i < 3; i++) {
    		values[i] = Math.round(Float.parseFloat(thresholds[i]) * 19808);
    		percentages[i] = Math.round(Float.parseFloat(thresholds[i]) * 100);
    	}
    	
    	PowerResponse response = new PowerResponse();
    	response.setData(graphResponseList);
    	response.setThresholds(values);
    	response.setThresholdPercentages(percentages);
    	
    	return response;
    }
    
    @GetMapping("/children/{username}/sessions/{sessionId}/cognitive-skills")
    public CognitiveSkillsResponse childMissionCognitiveSkills(@PathVariable("username") String username, 
    		@PathVariable("sessionId") String sessionId, HttpServletRequest request) throws Exception {
    	
    	PortalUser user = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	SearchResponse searchResponse = analyticsService.attemptCognitiveSkills(username, sessionId); 
    	
    	SearchHit[] hits = searchResponse.getHits().getHits();

    	return SearchResultsMapper.getCognitiveSkills(hits);
    }
    
    @GetMapping("/children/{username}/cognitive-skills")
    public CognitiveSkillsResponse latestCognitiveSkills(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	PortalUser user = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	List<String> attempts = analyticsService.parseAttempts(analyticsService.lastNRunners(username, 1));
    	
    	return childMissionCognitiveSkills(username, attempts.get(0), request);
    }
    
    @GetMapping("/children/{username}/cognitive-skills-progress")
    public CognitiveSkillsProgressResponse cognitiveSkillsProgress(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	PortalUser user = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	List<String> attempts = analyticsService.parseAttempts(analyticsService.lastNRunners(username, 2));
    	
    	CognitiveSkillsResponse lastScores = childMissionCognitiveSkills(username, attempts.get(0), request);
    	CognitiveSkillsResponse nextToLastScores = childMissionCognitiveSkills(username, attempts.get(1), request);
    	
    	return CognitiveSkillsProgressResponse
    		.builder()
    		.alternatingAttention(new SkillItem(lastScores.getAlternatingAttention(), nextToLastScores.getAlternatingAttention()))
    		.behavioralInhibition(new SkillItem(lastScores.getBehavioralInhibition(), nextToLastScores.getBehavioralInhibition()))
    		.cognitiveInhibition(new SkillItem(lastScores.getCognitiveInhibition(), nextToLastScores.getCognitiveInhibition()))
    		.delayOfGratification(new SkillItem(lastScores.getDelayOfGratification(), nextToLastScores.getDelayOfGratification()))
    		.dividedAttention(new SkillItem(lastScores.getDividedAttention(), nextToLastScores.getDividedAttention()))
    		.focusedAttention(new SkillItem(lastScores.getFocusedAttention(), nextToLastScores.getFocusedAttention()))
    		.innerVoice(new SkillItem(lastScores.getInnerVoice(), nextToLastScores.getInnerVoice()))
    		.motivationalInhibition(new SkillItem(lastScores.getMotivationalInhibition(), nextToLastScores.getMotivationalInhibition()))
    		.noveltyInhibition(new SkillItem(lastScores.getNoveltyInhibition(), nextToLastScores.getNoveltyInhibition()))
    		.selectiveAttention(new SkillItem(lastScores.getSelectiveAttention(), nextToLastScores.getSelectiveAttention()))
    		.selfRegulation(new SkillItem(lastScores.getSelfRegulation(), nextToLastScores.getSelfRegulation()))
    		.sustainedAttention(new SkillItem(lastScores.getSustainedAttention(), nextToLastScores.getSustainedAttention()))
    		.interferenceControl(new SkillItem(lastScores.getInterferenceControl(), nextToLastScores.getInterferenceControl()))
    		.build();
    }
    
    @GetMapping("/children/{username}/impulse-control")
    public List<ImpulseControl> impulseControl(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	PortalUser user = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	SearchResponse searchResponse = analyticsService.allCognitiveSkills(username); 
    	
    	SearchHit[] hits = searchResponse.getHits().getHits();

    	// make sure it's sorted in inserted order
    	Map<String, CognitiveSkillsResponse> attempts = new LinkedHashMap<>();
    	Map<String, Integer> missions = new HashMap<>();
    	
    	for (SearchHit hit : hits) {
    		
    		String attemptId = (String) hit.getSourceAsMap().get("session_start");
    		String missionId = (String) hit.getSourceAsMap().get("MissionID");
    		
    		if (missionId.equals("1.1"))
    			continue;
    		
    		int missionNo = Integer.parseInt(MappingService.getKey(missionId));
    		
    		CognitiveSkillsResponse skills = null;
    		
    		if ((skills = attempts.get(attemptId)) == null) {
    			
    			skills = new CognitiveSkillsResponse();
    			attempts.put(attemptId, skills);
    			missions.put(attemptId, missionNo);
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
    	
    	List<ImpulseControl> response = new ArrayList<>();
    	
    	for (Map.Entry<String, CognitiveSkillsResponse> entry : attempts.entrySet()) {
			
    		response.add(
    				ImpulseControl.fromSkills(
    						entry.getKey(),
    						entry.getValue(),
    						missions.get(entry.getKey())));
    	}
    	    	
    	return response;
    }

	@GetMapping("/children/{username}/sessions/{sessionId}/fog-analysis")
    public FogAnalysisResponse childMissionFogAnalysis(
    		@PathVariable("username") String username, 
    		@PathVariable("sessionId") String sessionId,
    		HttpServletRequest request) throws Exception {
    	
    	PortalUser user = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	SearchResponse xferResponse = analyticsService.transferenceEvents(username, sessionId);
    	
    	// Interesting xfer events
    	List<StartEnd> startEndList = new ArrayList<>();
    	SearchHit[] searchHits = xferResponse.getHits().getHits();
    	
    	double threshold = 0;
    	int target = 0;
    	int decoded = 0;
    	int frozen = 0;
    	String start = null;
    	String end = null;
    	
    	for (SearchHit searchHit : searchHits) {
			
    		Map<String, Object> source = searchHit.getSourceAsMap();
    		String eventType = (String)source.get("event_type");
    		
    		switch (eventType) {
    		
    		// start of xfer session
    		case "TransferenceStatsStart":
    			
    			target = (int)source.get("TargetDecodes");
    			break;
    			
    		// start of dish session
    		case "TransferenceStatsDishStart":
    			
    			threshold = (Double)source.get("DecodeThreshold");
    			break;
    			
    		// start of decoding session
    		case "TransferenceStatsMoleculeDecodeStart":
    			start = (String)source.get("timestamp");
    			break;
    		
    		// 1 molecule was decoded
    		case "TransferenceStatsMoleculeDecodeEnd":
    			decoded++;
    			break;
    			
    		// dish is frozen
    		// all molecules decoded
    		// end of decoding session
    		case "TransferenceStatsDishEnd":
    			
    			frozen++;
    			
    			
    			if (start != null) {
	    			end = (String)source.get("timestamp");
	    			startEndList.add(new StartEnd(start, end, threshold * 100));
	    			
	    			// reset
	    			start = null;
	    			threshold = 0;
    			}
    			break;
    		}
		}

    	FogAnalysisResponse fogAnalysisResponse = new FogAnalysisResponse();
    	fogAnalysisResponse.setDecodedMolecules(decoded);
    	fogAnalysisResponse.setFrozenDishes(frozen);
    	fogAnalysisResponse.setDishes(startEndList);
    	fogAnalysisResponse.setTargetDecodes(target);
    	fogAnalysisResponse.setPass(decoded >= target);
 	
    	return fogAnalysisResponse;
    }
    
    @GetMapping("/children/{username}/sessions/{sessionId}/attention")
    public AttentionResponse childMissionAttention(@PathVariable("username") String username, 
    		@PathVariable("sessionId") String sessionId, HttpServletRequest request) throws Exception {
    	
    	PortalUser user = jwtService.decodeJwtFromRequest(request, false, username);
    	
    	SearchResponse searchResponse = analyticsService.attention(username, sessionId); 
    	
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
    
    
   // ----------------- Hidden APIs
    @Hidden
    @GetMapping("/opensearch/completed-sessions")
    public SearchResponse completedSessions(HttpServletRequest request) throws Exception {
    	return analyticsService.completedSessions("388357544");
    }
    
    @Hidden
    @GetMapping("/opensearch/completed-sessions-week")
    public SearchResponse completedSessionsWeek(HttpServletRequest request) throws Exception {
    	return analyticsService.weeklyStats("388357544");
    }
    
    @Hidden
    @GetMapping("/opensearch/completed-missions")
    public SearchResponse completedMissions(HttpServletRequest request) throws Exception {
    	return analyticsService.completedMissions("1942396312");
    }

    @Hidden
    @GetMapping("/opensearch/missions")
    public SearchResponse missions(String userId, String missionId) throws Exception {
    	return analyticsService.attemptsPerMission("388357544", "5.2"); 
    }
    
    @Hidden
    @GetMapping("/opensearch/power")
    public SearchResponse power(HttpServletRequest request) throws Exception {
    	return analyticsService.power("388357544", "2023-04-24 16:28:16.566");
    }
    
    @Hidden
    @GetMapping("/opensearch/frozen-dishes")
    public CustomSearchResponse frozenDishes(HttpServletRequest request) throws Exception {
    	return analyticsService.frozenDishes("388357544", "2023-03-04 06:30:23.06");
    }
    
    @Hidden
    @GetMapping("/opensearch/decoded-molecules")
    public CountResponse decodedMolecules(HttpServletRequest request) throws Exception {
    	return analyticsService.decodedMolecules("388357544", "2023-02-25 10:15:39.971");
    }
    
    @Hidden
    @GetMapping("/opensearch/transference-events")
    public SearchResponse transferenceEvents(HttpServletRequest request) throws Exception {
    	return analyticsService.transferenceEvents("388357544", "2023-03-26 16:04:33.386");
    }
    
	@Hidden
	@GetMapping("/opensearch/attention")
	public SearchResponse attention(HttpServletRequest request) throws Exception {
		return analyticsService.attention("388357544", "2023-04-24 16:28:16.566");
	}
}