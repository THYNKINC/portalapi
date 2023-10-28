package com.portal.api.controllers;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.instancio.Instancio;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.filter.Filter;
import org.opensearch.search.aggregations.bucket.histogram.Histogram;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.opensearch.search.aggregations.metrics.Cardinality;
import org.opensearch.search.aggregations.metrics.ExtendedStats;
import org.opensearch.search.aggregations.metrics.Max;
import org.opensearch.search.aggregations.metrics.Min;
import org.opensearch.search.aggregations.metrics.Sum;
import org.opensearch.search.aggregations.metrics.TopHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.api.exception.ResourceNotFoundException;
import com.portal.api.model.Accuracy;
import com.portal.api.model.Attempt;
import com.portal.api.model.AttemptSummary;
import com.portal.api.model.Child;
import com.portal.api.model.CognitiveSkillsResponse;
import com.portal.api.model.CreateHeadsetRequest;
import com.portal.api.model.Crystals;
import com.portal.api.model.DashboardMetrics;
import com.portal.api.model.Dish;
import com.portal.api.model.GameState;
import com.portal.api.model.Headset;
import com.portal.api.model.HeadsetAssignment;
import com.portal.api.model.HistoricalProgressReport;
import com.portal.api.model.Obstacles;
import com.portal.api.model.PaginatedResponse;
import com.portal.api.model.Parent;
import com.portal.api.model.StarEarned;
import com.portal.api.model.SummaryReport;
import com.portal.api.model.TransferenceSummary;
import com.portal.api.model.UpdateChildRequest;
import com.portal.api.model.UpdateParentRequest;
import com.portal.api.repositories.HeadsetRepository;
import com.portal.api.services.AnalyticsService;
import com.portal.api.util.HttpService;
import com.portal.api.util.JwtService;
import com.portal.api.util.MappingService;
import com.portal.api.util.MongoService;
import com.portal.api.util.TimeUtil;

@RestController
@RequestMapping("/admin")
@Validated
public class AdminController {
	
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
	
	private final AnalyticsService analyticsService;
	
	private final HeadsetRepository headsets;
	
	private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    public AdminController(
    		JwtService jwtService,
    		MongoService mongoService,
    		AnalyticsService analyticsService,
    		HeadsetRepository headsets) {
        this.jwtService = jwtService;
        this.mongoService = mongoService;
        this.analyticsService = analyticsService;
        this.headsets = headsets;
    }
    
    @GetMapping("/me")
    public Parent getMe(HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	return mongoService.getParent(jwt.getClaim("cognito:username"));		
    }
    
    @GetMapping("/parents/{id}")
    public Parent getParent(@PathVariable("id") String id, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	
    	return mongoService.getParent(id);		
    }
    
    @GetMapping("/parents")
    public PaginatedResponse<Parent> getParents(HttpServletRequest request, @RequestParam(required = false) String partialName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	
    	Pageable pageRequest = PageRequest.of(page, size, Direction.ASC, "firstName", "lastName");
    	
    	if (StringUtils.hasText(partialName)) {
    		
    		Page<Parent> parents = mongoService.getParentsByNameStartingWith(partialName, pageRequest);
    		
    		return new PaginatedResponse<Parent>(parents.getContent(), parents.getTotalElements());
    	}
    	
    	Page<Parent> parents = mongoService.getAllParents(partialName, pageRequest);
    	
    	return new PaginatedResponse<Parent>(parents.getContent(), parents.getTotalElements());
    }
    
    @GetMapping("/children")
    public PaginatedResponse<Child> getChildren(HttpServletRequest request, @RequestParam(required = false) String partialName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	
    	Pageable pageRequest = PageRequest.of(page, size, Direction.ASC, "firstName", "lastName");
    	
    	if (StringUtils.hasText(partialName))
    		return mongoService.getChildrenByNameStartingWith(partialName, pageRequest);
    	
    	return mongoService.getAllChildren(pageRequest);
    }
    
    @GetMapping("/attempts")
    public PaginatedResponse<AttemptSummary> getAttempts(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	
    	SearchResponse searchResponse = analyticsService.attempts(page, size);
    	
    	PaginatedResponse<AttemptSummary> response = new PaginatedResponse<>();
    	response.setContent(new ArrayList<>());
    	
    	response.setTotal(searchResponse.getHits().getTotalHits().value);
    	
    	Set<String> uniqueUsers = new HashSet<>();
    	Set<String> uniqueMissionIds = new HashSet<>();
    	
    	// pass 1: get attempts
    	for (SearchHit hit : searchResponse.getHits().getHits()) {
    	
    		Map<String, Object> sourceAsMap = hit.getSourceAsMap();
    		
    		AttemptSummary attempt = new AttemptSummary();
    		attempt.setDate((String) sourceAsMap.get("session_start"));
    		attempt.setUsername((String) sourceAsMap.get("user_id"));
    		attempt.setMission((String) sourceAsMap.get("TaskID"));
    		attempt.setType(sourceAsMap.get("event_type").equals("RunnerEnd") ? "runner" : "transference");
    		
    		// TODO
    		attempt.setPass(false);
			attempt.setFirstName(attempt.getUsername());
			attempt.setLastName(attempt.getUsername());
			
			uniqueUsers.add(attempt.getUsername());
			uniqueMissionIds.add(attempt.getMission());
			
			response.getContent().add(attempt);
    	}
    	
    	// pass 2: get names
    	List<Child> children = mongoService.getChildrenByUsername(uniqueUsers);
    	
    	for (AttemptSummary attempt : response.getContent()) {
			
    		Child child = children.stream()
    				.filter(c -> c.getUsername().equals(attempt.getUsername()))
    				.findFirst()
    				.get();
    		
    		attempt.setFirstName(child.getFirstName());
    		attempt.setLastName(child.getLastName());
		}
    	
    	// pass 3: count tries
    	
    	return response;
    }
    
    @GetMapping("/dashboard")
    public DashboardMetrics getDashboard(HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	
    	SearchResponse searchResponse = analyticsService.dashboardMetrics();
    	
    	Filter active = searchResponse.getAggregations().get("active");
    	Terms activeSessions = active.getAggregations().get("sessions");
    	
    	double playtime = activeSessions.getBuckets().stream()
    		.collect(Collectors.summingDouble(b -> ((Max)b.getAggregations().get("duration")).getValue()));
    	
    	Cardinality users = active.getAggregations().get("users");
    	
    	Filter range = searchResponse.getAggregations().get("range");
    	Histogram daily = range.getAggregations().get("daily");
    	
    	SortedMap<String, Integer> sessions = new TreeMap<>();
    	SortedMap<String, Integer> missions = new TreeMap<>();
    	SortedMap<String, Integer> attempts = new TreeMap<>();
    	SortedMap<String, Integer> abandons = new TreeMap<>();
    	SortedMap<String, Integer> powers = new TreeMap<>();
    	    	
    	daily.getBuckets().forEach(bucket -> {
    		
    		Filter agg = bucket.getAggregations().get("missions");
    		missions.put(bucket.getKeyAsString(), (int)agg.getDocCount());
    		
    		agg = bucket.getAggregations().get("attempts");
    		long completed = agg.getDocCount();
    		attempts.put(bucket.getKeyAsString(), (int)completed);
    		
    		Histogram sessionsBuckets = agg.getAggregations().get("sessions");
    		sessions.put(bucket.getKeyAsString(), sessionsBuckets.getBuckets().size());
    		
    		agg = bucket.getAggregations().get("starts");
    		abandons.put(bucket.getKeyAsString(), (int)(agg.getDocCount() - completed));
    		
    		Max power = bucket.getAggregations().get("power");
    		powers.put(bucket.getKeyAsString(), Math.max(0, (int)power.value()));
    	});
    	
    	return DashboardMetrics.builder()
    			.abandons(abandons)
    			.attempts(attempts)
    			.missions(missions)
    			.power(powers)
    			.sessions(sessions)
    			.totalPlaytime(TimeUtil.prettyPrint(playtime)) // limit to 30 m
    			.totalUsers((int)users.getValue())
    			.build();
    }
    
    @GetMapping("/children/{username}")
    public Child getChild(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	List<Child> children = mongoService.getChildrenByUsername(Collections.singletonList(username));
    	
    	if (children.size() != 1)
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unable to find child with username " + username);
    	
    	return children.get(0);
    }
    
    @PutMapping("/parents/{username}")
    public Parent updateParentDetails(@PathVariable("username") String username, @RequestBody UpdateParentRequest update, HttpServletRequest request) throws Exception {
    	
    	Parent parent = mongoService.getParent(username);
    	
    	if (parent == null)
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unable to find parent with username " + username);
    	
    	parent.setFirstName(update.getFirstName());
    	parent.setLastName(update.getLastName());
    	parent.setAddress(update.getAddress());
    	parent.setCountry(update.getCountry());
    	parent.setZipCode(update.getZipCode());
    	parent.setCity(update.getCity());
    	
    	return mongoService.upsertParent(parent);
    }
    
    @PutMapping("/children/{username}")
    public Child updateChild(@PathVariable("username") String username, @RequestBody UpdateChildRequest update, HttpServletRequest request) throws Exception {
    	
    	List<Child> children = mongoService.getChildrenByUsername(Collections.singletonList(username));
    	
    	if (children.size() != 1)
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unable to find child with username " + username);
    	
    	Child child = children.get(0);
    	
    	child.setDob(update.getDob());
    	child.setFirstName(update.getFirstName());
    	child.setLastName(update.getLastName());
    	mongoService.updateChild(child);
    	
    	return child;
    }
    
    @GetMapping("/children/{username}/progress-report")
    public HistoricalProgressReport getChildProgressReport(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	
    	SearchResponse response = analyticsService.historicalProgress(username);
    	    	
    	Map<String, Aggregation> aggs = response.getAggregations().asMap();
    	
    	Min started = (Min)aggs.get("startDate");
    	Filter attempts = (Filter)aggs.get("attempts");
    	
    	Map<String, Aggregation> attemptAggs = attempts.getAggregations().asMap();
    	Max lastAttempt = (Max)attemptAggs.get("lastAttempt");
    	Histogram sessions = (Histogram)attemptAggs.get("sessions");
    	Sum completedPlaytime = (Sum)attemptAggs.get("playtime");
    	
    	Filter missions = (Filter)aggs.get("missions");
    	Map<String, Aggregation> missionAggs = missions.getAggregations().asMap();
    	Cardinality completedMissions = (Cardinality)missionAggs.get("idCount");
    	
    	long totalPlaytime = 0;
    	Filter active = (Filter)aggs.get("active");
    	Terms sessionGroup = (Terms)active.getAggregations().get("sessions");
    	
    	for (Terms.Bucket bucket : sessionGroup.getBuckets()) {
    		Max duration = bucket.getAggregations().get("duration");
    		totalPlaytime += duration.getValue();
    	}
    	
    	// TODO use value() instead, already a timestamp
    	long lastAttemptTs = df.parse(lastAttempt.getValueAsString()).getTime();
    	long startDateTs = df.parse(started.getValueAsString()).getTime();
    	
    	long today = new Date().getTime();
    	int totalDays = (int)TimeUnit.DAYS.convert(today - startDateTs, TimeUnit.MILLISECONDS);
    	
    	double weeks = totalDays / 7;
    	
    	HistoricalProgressReport builder = HistoricalProgressReport.builder()
    		.achievements(0)
    		.attemptsPerWeek(attempts.getDocCount() / weeks)
    		.daysSinceLastAttempt((int)TimeUnit.DAYS.convert(today - lastAttemptTs, TimeUnit.MILLISECONDS))
    		.daysSinceStart(totalDays)
    		.missionsCompleted((int)completedMissions.value())
    		.missionsPerWeek(completedMissions.value() / weeks)
    		.playtimeCompletedPerWeek(TimeUtil.prettyPrint(completedPlaytime.value() / weeks))
    		.playtimePerWeek(TimeUtil.prettyPrint(totalPlaytime / weeks))
    		.projectedCompletionDate(null)
    		.sessionsCompleted(sessions.getBuckets().size())
    		.sessionsPerWeek(sessions.getBuckets().size() / weeks)
    		.startDate(LocalDate.ofInstant(Instant.ofEpochMilli(startDateTs), TimeZone
    		        .getDefault().toZoneId()))
    		.totalAttempts((int)attempts.getDocCount())
    		.totalPlaytime(TimeUtil.prettyPrint(totalPlaytime))
    		.totalPlaytimeCompleted(TimeUtil.prettyPrint(completedPlaytime.value()))
    		.build();
    	
    	return builder;
    }
    
    @GetMapping("/children/{username}/transferences/{session_id}")
    public TransferenceSummary getTransference(@PathVariable("username") String username, @PathVariable("session_id") String sessionId, HttpServletRequest request) throws Exception {
    	
    	SearchResponse response = analyticsService.transference(username, sessionId);
    	    	
    	List<TransferenceSummary> transferences = new ArrayList<>();
    	
    	Filter session = response.getAggregations().get("session");
    		
		Min sessionStart = session.getAggregations().get("started");
		Max sessionEnd = session.getAggregations().get("ended");
		ExtendedStats bci = session.getAggregations().get("bci");
		Filter endEvent = session.getAggregations().get("end_event");
		Max target = session.getAggregations().get("target");
		
		Terms dishes = session.getAggregations().get("dishes");
		
		List<Dish> dishList = new ArrayList<>();
		
		for (Bucket dish: dishes.getBuckets()) {
    		
			Min dishStart = dish.getAggregations().get("dish_start");
			Max dishEnd = dish.getAggregations().get("dish_end");
			
			Filter decodes = dish.getAggregations().get("decodes");
			Min decodeStart = decodes.getAggregations().get("decode_start");
			Max decodeEnd = decodes.getAggregations().get("decode_end");
			Filter decoded = decodes.getAggregations().get("decoded");
			
			Filter actions = dish.getAggregations().get("actions");
			Min firstAction = actions.getAggregations().get("first_action");
			Max lastAction = actions.getAggregations().get("last_action");
			Filter rejections = actions.getAggregations().get("rejections");
			
			Filter display = dish.getAggregations().get("display");
			Min firstDisplayed = display.getAggregations().get("first_displayed");
			
			// order of things is
			// display start - display end - tap first - last selected - decode start - decode end
			
			dishList.add(Dish.builder()
					.decoded((int)decoded.getDocCount())
					.duration(TimeUtil.msToSec(dishStart, dishEnd))
					.decodeTime(TimeUtil.msToSec(decodeStart, decodeEnd))
					.gapTime(TimeUtil.msToSec(firstAction, decodeStart))
					.rejected((int)rejections.getDocCount())
					// here we assume that if there are no rejections, actions are selections
					.selected(rejections.getDocCount() > 0 ? 0 : (int)actions.getDocCount())
					.selectTime(TimeUtil.msToSec(firstDisplayed, lastAction))
					.tapTime(TimeUtil.msToSec(firstDisplayed, firstAction))
					.type(rejections.getDocCount() > 0 ? "rejected" : "selected")
					.build());
			
			
		}
		
		// TODO turn into method
		double decodeAvg = dishList.stream()
				.mapToInt(dish -> dish.getDecodeTime())
				.average().orElse(0);
		
		double decodeVariance = dishList.stream()
                .map(dish -> dish.getDecodeTime() - decodeAvg)
                .map(i -> i * i)
                .mapToDouble(i -> i).average().orElse(0);
		
		double gapAvg = dishList.stream()
				.mapToInt(dish -> dish.getGapTime())
				.average().orElse(0);
		
		double gapVariance = dishList.stream()
                .map(dish -> dish.getGapTime() - gapAvg)
                .map(i -> i * i)
                .mapToDouble(i -> i).average().orElse(0);
		
		double tapAvg = dishList.stream()
				.mapToInt(dish -> dish.getTapTime())
				.average().orElse(0);
		
		double tapVariance = dishList.stream()
                .map(dish -> dish.getTapTime() - tapAvg)
                .map(i -> i * i)
                .mapToDouble(i -> i).average().orElse(0);
		
		double selectAvg = dishList.stream()
				.mapToInt(dish -> dish.getSelectTime())
				.average().orElse(0);
		
		double selectVariance = dishList.stream()
                .map(dish -> dish.getSelectTime() - selectAvg)
                .map(i -> i * i)
                .mapToDouble(i -> i).average().orElse(0);
		
		int decoded = dishList.stream()
			.mapToInt(dish -> dish.getDecoded())
			.sum();
		
		return TransferenceSummary.builder()
				.bciAvg((int)bci.getAvg())
				.decodeAvg((int)decodeAvg)
				.decoded(decoded)
				.decodeStdDev((int)Math.sqrt(decodeVariance))
				.dishes(dishList)
				.duration(TimeUtil.msToMin(sessionStart, sessionEnd))
				.endDate((long)sessionEnd.getValue())
				.gapAvg((int)gapAvg)
				.gapStdDev((int)Math.sqrt(gapVariance))
				.pctDecoded((int)(decoded / target.value() * 100))
				.selectAvg((int)selectAvg)
				.selectStdDev((int)Math.sqrt(selectVariance))
				.startDate((long)sessionStart.getValue())
				.status(decoded > target.value() ? "PASS" : "FAIL")
				.completed(endEvent.getDocCount() > 0)
				.tapAvg((int)tapAvg)
				.tapStdDev((int)Math.sqrt(tapVariance))
				.target((int)target.value())
				.build();
    }
    
    @GetMapping("/children/{username}/stats")
    public SummaryReport getChildStats(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	SearchResponse response = analyticsService.summaryStats(username);
    	
    	return Instancio.create(SummaryReport.class);
    }
    
    private CognitiveSkillsResponse cognitiveScores(String username, String sessionId) throws Exception {
    	
    	SearchResponse searchResponse = analyticsService.attemptCognitiveSkills(username, sessionId); 
    	
    	SearchHit[] hits = searchResponse.getHits().getHits();

    	CognitiveSkillsResponse response = new CognitiveSkillsResponse();
    	    	
    	for (SearchHit hit : hits) {
    		
    		String metricName = (String) hit.getSourceAsMap().get("metric_type");
    		
    		Object o = hit.getSourceAsMap().get("metric_value");
    		Double value = 0d;
    		
    		if (o != null)
    			value = (Double)o ;
    	    
    	    switch (metricName) {
    	    	case "alternating_attention": 
    	    		response.setAlternatingAttention((int)Math.round(value));
    	    		break;
    	    	case "behavioral_inhibition": 
    	    		response.setBehavioralInhibition((int)Math.round(value));
    	    		break;
    	    	case "cognitive_inhibition": 
    	    		response.setCognitiveInhibition((int)Math.round(value));
    	    		break;
    	    	case "delayed_gratification": 
    	    		response.setDelayOfGratification((int)Math.round(value));
    	    		break;
    	    	case "divided_attention": 
    	    		response.setDividedAttention((int)Math.round(value));
    	    		break;
    	    	case "focused_attention": 
    	    		response.setFocusedAttention((int)Math.round(value));
    	    		break;
    	    	case "inner_voice": 
    	    		response.setInnerVoice((int)Math.round(value));
    	    		break;
    	    	case "interference_control": 
    	    		response.setInterferenceControl((int)Math.round(value));
    	    		break;
    	    	case "motivational_inhibition": 
    	    		response.setMotivationalInhibition((int)Math.round(value));
    	    		break;
    	    	case "novelty_inhibition": 
    	    		response.setNoveltyInhibition((int)Math.round(value));
    	    		break;
    	    	case "selective_attention": 
    	    		response.setSelectiveAttention((int)Math.round(value));
    	    		break;
    	    	case "self_regulation": 
    	    		response.setSelfRegulation((int)Math.round(value));
    	    		break;
    	    	case "sustained_attention": 
    	    		response.setSustainedAttention((int)Math.round(value));
    	    		break;
    	    	default:
    	    		throw new RuntimeException("Unknown metric name: " + metricName);
    	    }
    	}
    	    	
    	return response;
    }
    
    @GetMapping("/children/{username}/perf-report")
    public PaginatedResponse<Attempt> getChildSessions(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	SearchResponse response = analyticsService.sessions(username);
    	
    	Terms sessions = response.getAggregations().get("sessions");
    	
    	List<Attempt> attempts = new ArrayList<>();
    	
    	int attemptNo = 0;
    	
    	for (Terms.Bucket bucket : sessions.getBuckets()) {
    		
    		attemptNo++;
    		
    		Aggregations aggs = bucket.getAggregations();
    		
    		TopHits firstEvent = aggs.get("first_event");
    		Map<String, Object> firstDocFields = firstEvent.getHits().getHits()[0].getSourceAsMap();
    		
    		String sessionType = (String)firstDocFields.get("session_type");
    		
    		Max ended = ((Filter)aggs.get("actual-end")).getAggregations().get("ended");
    		Min started = aggs.get("started");
    		
    		long duration = (long)ended.getValue() - (long)started.getValue();
    		// sessions to 30 min in case of abandon
    		duration = Math.min(30*60, TimeUnit.SECONDS.convert(duration, TimeUnit.MILLISECONDS));
    		
    		Max power = aggs.get("power");
    		ExtendedStats bci = aggs.get("bci");
    		
    		Terms stars = aggs.get("stars");
    		List<StarEarned> starsEarned = stars.getBuckets().stream()
				.filter(b -> b.getKeyAsNumber().intValue() > 0)
    			.map(b -> {
    				Min atSecond = b.getAggregations().get("at_ts");
    				Min atScore = b.getAggregations().get("at_score");
    				long delta = (long)atSecond.getValue() - (long)started.getValue();
    				delta = TimeUnit.SECONDS.convert(delta, TimeUnit.MILLISECONDS);
    				return new StarEarned((int)delta, (int)atScore.getValue());
    			})
    			.collect(Collectors.toList());
    		
    		Terms results = aggs.get("results");
    		
    		int cs = 0;
    		int cr = 0;
    		int is = 0;
    		int ir = 0;
    		int impulses = 0;
    		
    		for(Bucket result: results.getBuckets()) {
    			
    			Terms actions = result.getAggregations().get("actions");
    			
    			for (Bucket action: actions.getBuckets()) {
    				
    				if (result.getKeyAsString().equals("Correct") && action.getKeyAsString().equals("ObjectStatusSelected")) cs++;
    				if (result.getKeyAsString().equals("Correct") && action.getKeyAsString().equals("ObjectStatusRejected")) cr++;
    				if (result.getKeyAsString().equals("Incorrect") && action.getKeyAsString().equals("ObjectStatusSelected")) is++;
    				if (result.getKeyAsString().equals("Incorrect") && action.getKeyAsString().equals("ObjectStatusRejected")) ir++;
    				if (result.getKeyAsString().equals("Impulse")) impulses++;
    			}
    		}
    		
    		int opportunities = cs + cr + is + ir + impulses;
    		
    		Filter crystals = aggs.get("crystals");
    		Terms crystalOutcome = crystals.getAggregations().get("outcomes");
    		int collectedCrystals = (int) crystalOutcome.getBuckets()
				.stream()
				.filter(b -> b.getKeyAsString().equals("ObjectStatusCollected"))
				.count();
    		int missedCrystals = (int) crystalOutcome.getBuckets()
				.stream()
				.filter(b -> b.getKeyAsString().equals("ObjectStatusOutOfRange"))
				.count();
    		int totalCrystals = collectedCrystals + missedCrystals;
    		
    		Filter obstacles = aggs.get("crystals");
    		Terms obstacleOutcome = obstacles.getAggregations().get("outcomes");
    		int collidedObstacles = (int) obstacleOutcome.getBuckets()
				.stream()
				.filter(b -> b.getKeyAsString().equals("ObjectStatusCollided"))
				.count();
    		int avoidedObstacles = (int) crystalOutcome.getBuckets()
				.stream()
				.filter(b -> b.getKeyAsString().equals("ObjectStatusOutOfRange"))
				.count();
    		int totalObstacles = collidedObstacles + avoidedObstacles;
    		
    		Filter completed = aggs.get("completed");
    		
    		Filter decoded = aggs.get("decoded");
    		Max decodesTarget = aggs.get("decodes_target");
    		
    		boolean pass = sessionType.equals("runner") ?
				starsEarned.size() > 0
				: decoded != null && decoded.getDocCount() > decodesTarget.getValue();
    		
    		Attempt attempt = Attempt.builder()
	    		.accuracy(Accuracy.builder()
	    			.opportunities(opportunities)
	    			.correctRejected(cr)
	    			.correctSelected(cs)
	    			.incorrectRejected(ir)
	    			.incorrectSelected(is)
	    			.impulses(impulses)
	    			.build())
	    		.attemptNumber(attemptNo)
	    		.badges(null)
	    		.bciMean((int)Math.round(bci.getAvg()))
	    		.bciStdDeviation((int)Math.round(bci.getStdDeviation()))
	    		.completed(completed.getDocCount() > 0 ? true : false)
	    		.duration((int)duration)
	    		.endTime((long)ended.getValue())
	    		.id((String)firstDocFields.get("session_start"))
	    		.maxPower((int)power.getValue())
	    		.missionId(Integer.valueOf(MappingService.getKey((String)firstDocFields.get("MissionID"))))
	    		.ranks(null)
	    		.responseTime(0)
	    		.scores(null)
	    		.stars(starsEarned)
	    		.startTime((long)started.getValue())
	    		.status(pass ? "PASS" : "FAIL")
	    		.tierAvg(0)
	    		.type(sessionType)
	    		.build();
    		
    		if (sessionType.equals("runner")) {
	    		
    			if (totalCrystals > 0) {
	    			attempt.setCrystals(Crystals.builder()
						.collected(collectedCrystals)
						.pctCollected(collectedCrystals / totalCrystals * 100)
						.missed(missedCrystals)
						.record(0)
						.build());
    			}
	    		
    			if (totalObstacles > 0) {
		    		attempt.setObstacles(Obstacles.builder()
	    				.avoided(avoidedObstacles)
	    				.collided(collidedObstacles)
	    				.pctAvoided(avoidedObstacles / totalObstacles * 100)
	    				.record(0)
	    				.build());
    			}
    		}
    		
    		attempts.add(attempt);
    	}
    	
    	return new PaginatedResponse<>(attempts, attempts.size());
    }
    
    @GetMapping("/children/{username}/state")
    public GameState getChildState(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	
    	// TODO change to use Spring web request, which includes JWT exchange
        String bearerToken = jwtService.getAdminJwt();
        
        // TODO change to bean
        // TODO change to use Spring web request
        String url = String.format("http://%s:%s/games/users/%s/game-state", GAMES_SERVICE, GAMES_PORT, username);
        String result = HttpService.sendHttpGetRequest(url, bearerToken);
        if (result == null) {
        	throw new ResourceNotFoundException("No game state for this user");
        }
        
        GameState state;
        try {
        	ObjectMapper mapper = new ObjectMapper();
        	state = mapper.readValue(result, GameState.class);
        	return state;
        } catch (JsonMappingException e) {
    	   throw new ResourceNotFoundException("Resource not found");
    	}
    }
    
    @PostMapping("/headsets")
    public Headset createHeadset(@RequestBody CreateHeadsetRequest headset, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	
    	if (headsets.findById(headset.getId()).isPresent())
    		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset ID already exists");
    	
    	Headset newHeadset = new Headset();
    	newHeadset.setId(headset.getId());
    	newHeadset.setManufacteDate(headset.getManufacteDate());
    	
    	return headsets.insert(newHeadset);
    }
    
    @PutMapping("/headsets/{id}")
    public Headset updateHeadset(@PathVariable String id, @RequestBody Headset headset, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	
    	Headset old = headsets.findById(id)
    			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset with that ID doesn't exist"));
    	
    	old.setManufacteDate(headset.getManufacteDate());
    	
    	return headsets.save(headset);
    }
    
    @GetMapping("/headsets")
    public Page<Headset> listHeadsets(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	
    	Pageable pageRequest = PageRequest.of(page, size);
    	
    	return headsets.findAll(pageRequest);
    }
    
    @GetMapping("/headsets/{id}")
    public Headset getHeadsets(@PathVariable String id, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	    	
    	return headsets.findById(id)
    			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset with that ID doesn't exist"));
    }
    
    @PostMapping("/headsets/{id}/assignment")
    public Headset assignHeadsets(@PathVariable String id, @RequestBody HeadsetAssignment assignment, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	    	
    	Headset headset = headsets.findById(id)
    			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset with that ID doesn't exist"));
    	
    	if (headset.getPlayer() != null && headset.getFirstUseTimestamp() != 0)
    		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset is already in use");
    	
    	Child child = mongoService.getChildrenByUsername(Collections.singletonList(assignment.getUsername())).get(0);
    	
    	child.setHeadsetId(id);
    	mongoService.updateChild(child);
    	
    	headset.setPlayer(assignment.getUsername());
    	return headsets.save(headset);
    }
}