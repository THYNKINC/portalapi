package com.portal.api.controllers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.api.exception.ResourceNotFoundException;
import com.portal.api.model.Attempt;
import com.portal.api.model.Child;
import com.portal.api.model.GameState;
import com.portal.api.model.PaginatedResponse;
import com.portal.api.model.Parent;
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

    @Autowired
    public AdminController(
    		JwtService jwtService,
    		MongoService mongoService,
    		AnalyticsService analyticsService) {
        this.jwtService = jwtService;
        this.mongoService = mongoService;
        this.analyticsService = analyticsService;
    }
    
    @GetMapping("/me")
    public Parent getParent(HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, null);
    	return mongoService.getParent(jwt.getClaim("cognito:username"));		
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
    public PaginatedResponse<Attempt> getAttempts(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) throws Exception {
    	
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false, null);
    	
    	SearchResponse searchResponse = analyticsService.attempts(page, size);
    	
    	PaginatedResponse<Attempt> response = new PaginatedResponse<>();
    	response.setContent(new ArrayList<>());
    	
    	response.setTotal(searchResponse.getHits().getTotalHits().value);
    	
    	Set<String> uniqueUsers = new HashSet<>();
    	Set<String> uniqueMissionIds = new HashSet<>();
    	
    	// pass 1 get attempts
    	for (SearchHit hit : searchResponse.getHits().getHits()) {
    	
    		Map<String, Object> sourceAsMap = hit.getSourceAsMap();
    		
    		Attempt attempt = new Attempt();
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
    	
    	for (Attempt attempt : response.getContent()) {
			
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
    
    
}