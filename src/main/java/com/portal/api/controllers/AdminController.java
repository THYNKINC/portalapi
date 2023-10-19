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
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.instancio.Instancio;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.bucket.filter.Filter;
import org.opensearch.search.aggregations.bucket.histogram.Histogram;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.metrics.Cardinality;
import org.opensearch.search.aggregations.metrics.Max;
import org.opensearch.search.aggregations.metrics.Min;
import org.opensearch.search.aggregations.metrics.Sum;
import org.opensearch.search.aggregations.pipeline.AvgBucketPipelineAggregator;
import org.opensearch.search.aggregations.pipeline.ParsedSimpleValue;
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
import com.portal.api.model.GameState;
import com.portal.api.model.Headset;
import com.portal.api.model.HeadsetAssignment;
import com.portal.api.model.HistoricalProgressReport;
import com.portal.api.model.PaginatedResponse;
import com.portal.api.model.Parent;
import com.portal.api.model.SummaryReport;
import com.portal.api.model.UpdateChildRequest;
import com.portal.api.model.UpdateParentRequest;
import com.portal.api.repositories.HeadsetRepository;
import com.portal.api.services.AnalyticsService;
import com.portal.api.util.HttpService;
import com.portal.api.util.JwtService;
import com.portal.api.util.MongoService;

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
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, null);
    	return mongoService.getParent(jwt.getClaim("cognito:username"));		
    }
    
    @GetMapping("/parents/{id}")
    public Parent getParent(@PathVariable("id") String id, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, null);
    	
    	return mongoService.getParent(id);		
    }
    
    @GetMapping("/parents")
    public PaginatedResponse<Parent> getParents(HttpServletRequest request, @RequestParam(required = false) String partialName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, null);
    	
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
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, null);
    	
    	Pageable pageRequest = PageRequest.of(page, size, Direction.ASC, "firstName", "lastName");
    	
    	if (StringUtils.hasText(partialName))
    		return mongoService.getChildrenByNameStartingWith(partialName, pageRequest);
    	
    	return mongoService.getAllChildren(pageRequest);
    }
    
    @GetMapping("/attempts")
    public PaginatedResponse<AttemptSummary> getAttempts(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, null);
    	
    	SearchResponse searchResponse = analyticsService.attempts(page, size);
    	
    	PaginatedResponse<AttemptSummary> response = new PaginatedResponse<>();
    	response.setContent(new ArrayList<>());
    	
    	response.setTotal(searchResponse.getHits().getTotalHits().value);
    	
    	Set<String> uniqueUsers = new HashSet<>();
    	Set<String> uniqueMissionIds = new HashSet<>();
    	
    	// pass 1 get attempts
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
    	
    	// pass 2 get names
    	List<Child> children = mongoService.getChildrenByUsername(uniqueUsers);
    	
    	for (AttemptSummary attempt : response.getContent()) {
			
    		Child child = children.stream()
    				.filter(c -> c.getUsername().equals(attempt.getUsername()))
    				.findFirst()
    				.get();
    		
    		attempt.setFirstName(child.getFirstName());
    		attempt.setLastName(child.getLastName());
		}
    	
    	// pass 3 count tries
    	
    	return response;
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
    		.playtimeCompletedPerWeek(completedPlaytime.value() / weeks)
    		.playtimePerWeek(totalPlaytime / weeks)
    		.projectedCompletionDate(null)
    		.sessionsCompleted(sessions.getBuckets().size())
    		.sessionsPerWeek(sessions.getBuckets().size() / weeks)
    		.startDate(LocalDate.ofInstant(Instant.ofEpochMilli(startDateTs), TimeZone
    		        .getDefault().toZoneId()))
    		.totalAttempts((int)attempts.getDocCount())
    		.totalPlaytime(totalPlaytime)
    		.totalPlaytimeCompleted((long)completedPlaytime.value())
    		.build();
    	
    	return builder;
    }
    
    @GetMapping("/children/{username}/stats")
    public SummaryReport getChildStats(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	SearchResponse response = analyticsService.summaryStats(username);
    	
    	return Instancio.create(SummaryReport.class);
    }
    
    @GetMapping("/children/{username}/perf-report")
    public Page<Attempt> getChildSessions(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	Attempt attempt = Attempt.builder()
    		.accuracy(Accuracy.builder()
    			.opportunities(0)
    			.correctRejected(0)
    			.correctSelected(0)
    			.incorrectRejected(0)
    			.incorrectSelected(0)
    			.build())
    		.attemptNumber(0)
    		.badges(null)
    		.bciMean(0)
    		.bciStdDeviation(0)
    		.crystals(null)
    		.duration(0)
    		.endTime(0)
    		.maxPower(0)
    		.missionId(0)
    		.obstacles(null)
    		.ranks(null)
    		.responseTime(0)
    		.scores(null)
    		.stars(null)
    		.startTime(0)
    		.status(username)
    		.tierAvg(0)
    		.build();
    		
    	return Instancio.create(Page.class);
    }
    
    @GetMapping("/children/{username}/state")
    public GameState getChildState(@PathVariable("username") String username, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, username);
    	
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
    public Headset createHeadset(@RequestBody Headset headset, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	
    	if (headsets.findById(headset.getId()).isPresent())
    		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset ID already exists");
    	
    	return headsets.insert(headset);
    }
    
    @PutMapping("/headsets/{id}")
    public Headset updateHeadset(@PathVariable String id, @RequestBody Headset headset, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	
    	Headset old = headsets.findById(id)
    			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset with that ID doesn't exist"));
    	
    	if (!headset.getId().equals(id))
    		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset ID cannot be changed");
    	
    	old.setManufacteDate(headset.getManufacteDate());
    	
    	return headsets.insert(headset);
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