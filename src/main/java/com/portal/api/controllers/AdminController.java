package com.portal.api.controllers;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.instancio.Instancio;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.bucket.filter.Filter;
import org.opensearch.search.aggregations.bucket.histogram.Histogram;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.metrics.Avg;
import org.opensearch.search.aggregations.metrics.Cardinality;
import org.opensearch.search.aggregations.metrics.Max;
import org.opensearch.search.aggregations.metrics.Sum;
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
import com.portal.api.model.AttemptSummary;
import com.portal.api.model.Child;
import com.portal.api.model.CognitiveSkillsResponse;
import com.portal.api.model.CreateHeadsetRequest;
import com.portal.api.model.DashboardMetrics;
import com.portal.api.model.GameState;
import com.portal.api.model.Headset;
import com.portal.api.model.HeadsetAssignment;
import com.portal.api.model.HistoricalProgressReport;
import com.portal.api.model.PVTSummary;
import com.portal.api.model.PaginatedResponse;
import com.portal.api.model.Parent;
import com.portal.api.model.RunnerSummary;
import com.portal.api.model.SessionSummary;
import com.portal.api.model.SummaryReport;
import com.portal.api.model.TransferenceSummary;
import com.portal.api.model.UpdateChildRequest;
import com.portal.api.model.UpdateParentRequest;
import com.portal.api.repositories.HeadsetRepository;
import com.portal.api.services.AnalyticsService;
import com.portal.api.services.SearchResultsMapper;
import com.portal.api.util.HttpService;
import com.portal.api.util.JwtService;
import com.portal.api.util.ParentService;
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
	
	private final ParentService mongoService;
	
	private final AnalyticsService analyticsService;
	
	private final HeadsetRepository headsets;
	
	private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    public AdminController(
    		JwtService jwtService,
    		ParentService mongoService,
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
    	
    	DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
    	
    	for (SearchHit hit : searchResponse.getHits().getHits()) {
    	
    		Map<String, Object> sourceAsMap = hit.getSourceAsMap();
    		
    		AttemptSummary attempt = new AttemptSummary();
    		attempt.setDate(df.format(new Date((Long) sourceAsMap.get("start_date"))));
    		attempt.setUsername((String) sourceAsMap.get("user_id"));
    		attempt.setMission((Integer) sourceAsMap.get("mission_id") + "");
    		attempt.setType((String)sourceAsMap.get("type"));
    		
    		// TODO
    		attempt.setPass(sourceAsMap.get("status").equals("PASS") ? true : false);
			attempt.setFirstName((String)sourceAsMap.get("first_name"));
			attempt.setLastName((String)sourceAsMap.get("last_name"));
			
			response.getContent().add(attempt);
    	}
    	
    	return response;
    }
    
    @GetMapping("/dashboard")
    public DashboardMetrics getDashboard(@RequestParam(required = false, defaultValue = "daily") String scale, @RequestParam(required = false) String type, HttpServletRequest request) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, true, null);
    	
    	SearchResponse searchResponse = analyticsService.dashboardMetrics(scale, type);
    	
    	Cardinality users = searchResponse.getAggregations().get("users");
    	Sum playtime = searchResponse.getAggregations().get("playtime");
    	
    	Filter range = null;
    	
    	if (type != null)
    		range = ((Filter)searchResponse
    			.getAggregations().get("by_type"))
    			.getAggregations().get("range");
    	else
    		range = searchResponse.getAggregations().get("range");
    	
    	Histogram daily = range.getAggregations().get("dates");
    	
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
    		sessions.put(bucket.getKeyAsString(), sessionsBuckets.getBuckets().stream()
    				.mapToInt(b -> (int)b.getDocCount())
    				.sum());
    		
    		abandons.put(bucket.getKeyAsString(), (int)(bucket.getDocCount() - completed));
    		
    		Avg power = bucket.getAggregations().get("power");
    		powers.put(bucket.getKeyAsString(), power.getValueAsString() != "Infinity" ? (int)Math.round(power.value()) : 0);
    	});
    	
    	return DashboardMetrics.builder()
    			.abandons(abandons)
    			.attempts(attempts)
    			.missions(missions)
    			.power(powers)
    			.sessions(sessions)
    			.totalPlaytime(TimeUtil.prettyPrint(playtime.getValue() * 1000)) // limit to 30 m
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
    	
    	SearchResponse response = analyticsService.historicalProgress(username);

    	return HistoricalProgressReport.parse(response);
    }
    
    @GetMapping("/children/{username}/runners/{session_id}")
    public RunnerSummary getRunner(@PathVariable("username") String username, @PathVariable("session_id") String sessionId, HttpServletRequest request) throws Exception {
    	
    	SearchResponse response = analyticsService.pvt(username, sessionId);
    	
    	return SearchResultsMapper.getRunner(response, username, sessionId);
    }
    
    @GetMapping("/children/{username}/pvt/{session_id}")
    public PVTSummary getPvt(@PathVariable("username") String username, @PathVariable("session_id") String sessionId, HttpServletRequest request) throws Exception {
    	
    	SearchResponse response = analyticsService.pvt(username, sessionId);
    	
    	return SearchResultsMapper.getPvt(response, username, sessionId);
    }
    
    @GetMapping("/children/{username}/transferences/{session_id}")
    public TransferenceSummary getTransference(@PathVariable("username") String username, @PathVariable("session_id") String sessionId, HttpServletRequest request) throws Exception {
    	
    	SearchResponse response = analyticsService.session(username, sessionId);
    	return (TransferenceSummary)SearchResultsMapper.getSession(response.getHits().getHits()[0]);
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
    public PaginatedResponse<SessionSummary> getChildSessions(@PathVariable("username") String username, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, HttpServletRequest request) throws Exception {
    	
    	SearchResponse response = analyticsService.sessions(username, PageRequest.of(page, size));
    	List<SessionSummary> sessions = new ArrayList<>();

    	for (SearchHit hit: response.getHits().getHits()) {
    		
    		sessions.add(SearchResultsMapper.getSession(hit));
		}
    	
    	return new PaginatedResponse<>(sessions, response.getHits().getTotalHits().value);
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