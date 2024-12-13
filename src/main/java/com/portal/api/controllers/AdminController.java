package com.portal.api.controllers;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.api.dto.request.CreateDelegateRequest;
import com.portal.api.dto.request.CreateHeadsetRequest;
import com.portal.api.dto.request.UpdateChildRequest;
import com.portal.api.dto.request.UpdateParentRequest;
import com.portal.api.dto.response.ChildWithCohortType;
import com.portal.api.dto.response.GraphResponse;
import com.portal.api.dto.response.PaginatedResponse;
import com.portal.api.exception.ResourceNotFoundException;
import com.portal.api.model.*;
import com.portal.api.repositories.DelegateRepository;
import com.portal.api.repositories.HeadsetRepository;
import com.portal.api.services.*;
import com.portal.api.util.DateTimeUtil;
import com.portal.api.util.HttpService;
import com.portal.api.util.JwtService;
import io.swagger.v3.oas.annotations.Parameter;
import org.instancio.Instancio;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.bucket.filter.Filter;
import org.opensearch.search.aggregations.bucket.histogram.Histogram;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.metrics.Avg;
import org.opensearch.search.aggregations.metrics.Cardinality;
import org.opensearch.search.aggregations.metrics.ExtendedStats;
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
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@Validated
public class AdminController {

    private final ImportJobService importJobService;
    private final CoachService coachService;

    @Value("${app-client-id}")
    private String APP_CLIENT_ID;

    @Value("${games-port}")
    private String GAMES_PORT;

    @Value("${games-service}")
    private String GAMES_SERVICE;

    @Value("${group-name-user}")
    private String GROUP_NAME_USER;

    @Value("${group-name-delegate}")
    private String GROUP_NAME_DELEGATE;

    @Value("${user-pool-id}")
    private String USER_POOL_ID;

    private final JwtService jwtService;

    private final ParentService parentService;

    private final DelegateRepository delegates;

    private final AnalyticsService analyticsService;

    private final HeadsetRepository headsets;

    private final CohortService cohortService;

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    public AdminController(
            JwtService jwtService,
            ParentService parentService,
            AnalyticsService analyticsService,
            HeadsetRepository headsets,
            DelegateRepository delegates,
            CohortService cohortService, ImportJobService importJobService, CoachService coachService) {
        this.jwtService = jwtService;
        this.parentService = parentService;
        this.analyticsService = analyticsService;
        this.headsets = headsets;
        this.delegates = delegates;
        this.cohortService = cohortService;
        this.importJobService = importJobService;
        this.coachService = coachService;
    }

    @PostMapping("/delegates")
    public void createDelegate(@Valid @RequestBody CreateDelegateRequest createParentRequest, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

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

        // Add user to user group
        AdminAddUserToGroupRequest addUserToGroupRequest = AdminAddUserToGroupRequest.builder()
                .userPoolId(USER_POOL_ID)
                .username(createParentRequest.getEmail())
                .groupName(GROUP_NAME_DELEGATE)
                .build();

        cognitoClient.adminAddUserToGroup(addUserToGroupRequest);

        AdminConfirmSignUpRequest confirmSignUpRequest = AdminConfirmSignUpRequest.builder()
                .userPoolId(USER_POOL_ID)
                .username(createParentRequest.getEmail())
                .build();

        AdminConfirmSignUpResponse confirmSignUpResponse = cognitoClient.adminConfirmSignUp(confirmSignUpRequest);

        boolean isConfirmed = confirmSignUpResponse.sdkHttpResponse().isSuccessful();

        Delegate delegate = new Delegate();
        delegate.setCreatedDate(new Date());
        delegate.setChildren(new ArrayList<>());
        delegate.setEmail(createParentRequest.getEmail());
        delegate.setFirstName(createParentRequest.getFirstName());
        delegate.setLastName(createParentRequest.getLastName());
        delegate.setUsername(signUpResponse.userSub());

        delegates.save(delegate);
    }

    @GetMapping("/me")
    public PortalUser getMe(HttpServletRequest request) throws Exception {
        return jwtService.decodeJwtFromRequest(request, true, null);
    }

    @GetMapping("/parents/{id}")
    public Parent getParent(@PathVariable("id") String id, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        return parentService.getParent(id);
    }

    @GetMapping("/parents")
    public PaginatedResponse<Parent> getParents(HttpServletRequest request, @RequestParam(required = false) String partialName,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        Pageable pageRequest = PageRequest.of(page, size, Direction.DESC, "createdDate");

        if (StringUtils.hasText(partialName)) {

            Page<Parent> parents = parentService.getParentsByNameStartingWith(partialName, pageRequest);

            return new PaginatedResponse<Parent>(parents.getContent(), parents.getTotalElements());
        }

        Page<Parent> parents = parentService.getAllParents(partialName, pageRequest);

        return new PaginatedResponse<Parent>(parents.getContent(), parents.getTotalElements());
    }

    @GetMapping("/children")
    public PaginatedResponse<Child> getChildren(HttpServletRequest request,
                                                @Parameter(description = "The begining of the child's username")
                                                @RequestParam(required = false) String partialName,
                                                @Parameter(description = "key-value pairs to filter by label, where key needs to start with l_. Ex: l_region=test&l_cohort=test")
                                                @RequestParam(required = false) Map<String, String> labels,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        Pageable pageRequest = PageRequest.of(page, size, Direction.ASC, "firstName", "lastName");

        return parentService.getChildrenByFilter(partialName, labels, pageRequest);
    }

    @GetMapping("/cohorts/all")
    ResponseEntity<List<Cohort>> all(HttpServletRequest request) throws Exception {

        PortalUser coach = jwtService.decodeJwtFromRequest(request, true, null);

        return ResponseEntity.ok(cohortService.getAllCohorts());
    }

    @GetMapping("/attempts")
    public PaginatedResponse<AttemptSummary> getAttempts(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        SearchResponse searchResponse = analyticsService.attempts(page, size);

        PaginatedResponse<AttemptSummary> response = new PaginatedResponse<>();
        response.setContent(new ArrayList<>());
        response.setTotal(searchResponse.getHits().getTotalHits().value);

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

        for (SearchHit hit : searchResponse.getHits().getHits()) {

            Map<String, Object> sourceAsMap = hit.getSourceAsMap();

            AttemptSummary attempt = new AttemptSummary();
            attempt.setDate(df.format(new Date((Long) sourceAsMap.get("start_date"))));
            attempt.setUsername((String) sourceAsMap.get("user_id"));
            attempt.setMission((Integer) sourceAsMap.get("mission_id") + "");
            attempt.setType((String) sourceAsMap.get("type"));

            // TODO
            attempt.setPass(sourceAsMap.get("status").equals("PASS") ? true : false);
            attempt.setFirstName((String) sourceAsMap.get("first_name"));
            attempt.setLastName((String) sourceAsMap.get("last_name"));

            response.getContent().add(attempt);
        }

        return response;
    }

    @GetMapping("/dashboard")
    public DashboardMetrics getDashboard(@RequestParam(required = false, defaultValue = "daily") String scale, @RequestParam(required = false) String type, HttpServletRequest request) throws Exception {

        Map<String, Integer> scales = Map.of("daily", 10, "weekly", 10, "monthly", 7, "yearly", 4);
        int dateLength = scales.get(scale);

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        SearchResponse searchResponse = analyticsService.dashboardMetrics(scale, type);

        Cardinality users = searchResponse.getAggregations().get("users");
        Sum playtime = searchResponse.getAggregations().get("playtime");

        Filter range = null;

        if (type != null)
            range = ((Filter) searchResponse
                    .getAggregations().get("by_type"))
                    .getAggregations().get("range");
        else
            range = searchResponse.getAggregations().get("range");

        // daily stats
        Histogram daily = range.getAggregations().get("dates");

        SortedMap<String, Integer> sessions = new TreeMap<>();
        SortedMap<String, Integer> missions = new TreeMap<>();
        SortedMap<String, Integer> attempts = new TreeMap<>();
        SortedMap<String, Integer> abandons = new TreeMap<>();
        SortedMap<String, Integer> powers = new TreeMap<>();

        daily.getBuckets().forEach(bucket -> {

            Filter agg = bucket.getAggregations().get("missions");
            missions.put(bucket.getKeyAsString().substring(0, dateLength), (int) agg.getDocCount());

            agg = bucket.getAggregations().get("attempts");
            long completed = agg.getDocCount();
            attempts.put(bucket.getKeyAsString().substring(0, dateLength), (int) completed);

            Histogram sessionsBuckets = agg.getAggregations().get("sessions");
            sessions.put(bucket.getKeyAsString().substring(0, dateLength), sessionsBuckets.getBuckets().stream()
                    .mapToInt(b -> (int) b.getDocCount())
                    .sum());

            abandons.put(bucket.getKeyAsString().substring(0, dateLength), (int) (bucket.getDocCount() - completed));

            Avg power = bucket.getAggregations().get("power");
            powers.put(bucket.getKeyAsString().substring(0, dateLength), power.getValueAsString() != "Infinity" ? (int) Math.round(power.value()) : 0);
        });

        // runner composite scores
        Filter runnerStats = searchResponse.getAggregations().get("runner_stats");
        Terms runnerMissions = runnerStats.getAggregations().get("missions");

        List<CompositeScores> compositeScores = runnerMissions.getBuckets().stream()
                .sorted((b1, b2) -> Integer.valueOf(b1.getKeyAsString()).compareTo(Integer.valueOf(b2.getKeyAsString())))
                .map(bucket -> {

                    ExtendedStats focus = bucket.getAggregations().get("focus");
                    ExtendedStats impulse = bucket.getAggregations().get("impulse");

                    return CompositeScores.builder()
                            .focus(Stats.map(focus))
                            .impulse(Stats.map(impulse))
                            .build();
                })
                .collect(Collectors.toList());

        return DashboardMetrics.builder()
                .abandons(abandons)
                .attempts(attempts)
                .compositeScores(compositeScores)
                .missions(missions)
                .power(powers)
                .sessions(sessions)
                .totalPlaytime(DateTimeUtil.prettyPrint(playtime.getValue() * 1000)) // limit to 30 m
                .totalUsers((int) users.getValue())
                .build();
    }

    @GetMapping("/children/{username}")
    public ChildWithCohortType getChild(@PathVariable("username") String username, HttpServletRequest request) {

        List<Child> children = parentService.getChildrenByUsername(Collections.singletonList(username));

        if (children.isEmpty()) {
            children = cohortService.getChildrenByUsername(Collections.singletonList(username));
        }

        if (children.size() != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unable to find child with username " + username);

        ChildWithCohortType childWithCohortType = new ChildWithCohortType();

        Child child = children.get(0);

        String cohortId = child.getLabels().get("cohort");
        if (cohortId != null) {
            try {
                Cohort cohort = cohortService.getCohort(cohortId);
                childWithCohortType.setCohortType(cohort.getPlayerType());
            } catch (Exception e) {
                childWithCohortType.setCohortType("None");
            }
        }

        childWithCohortType.setChild(child);

        return childWithCohortType;
    }

    @PutMapping("/parents/{username}")
    public Parent updateParentDetails(@PathVariable("username") String username, @RequestBody UpdateParentRequest update, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        Parent parent = parentService.getParent(username);

        if (parent == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unable to find parent with username " + username);

        parent.setFirstName(update.getFirstName());
        parent.setLastName(update.getLastName());
        parent.setAddress(update.getAddress());
        parent.setCountry(update.getCountry());
        parent.setZipCode(update.getZipCode());
        parent.setCity(update.getCity());

        return parentService.upsertParent(parent);
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<String> deleteAllSessions(HttpServletRequest request) {
        try {
            analyticsService.deleteAllSessions();
            return ResponseEntity.ok("All session data deleted successfully");
        } catch (Exception e) {
            logger.error("Error deleting session data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete session data");
        }
    }

    @PutMapping("/children/{username}")
    public Child updateChild(@PathVariable("username") String username, @RequestBody UpdateChildRequest update, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        boolean isInCohort = false;

        List<Child> children = parentService.getChildrenByUsername(Collections.singletonList(username));
        if (children.size() != 1) {
            children = cohortService.getChildrenByUsername(Collections.singletonList(username));
            if (children.size() != 1) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unable to find child with username " + username);
            } else {
                isInCohort = true;
            }
        }

        Child child = children.get(0);
        child.setDob(update.getDob());
        child.setFirstName(update.getFirstName());
        child.setLastName(update.getLastName());
        child.setLabels(update.getLabels());
        child.setUpdatedDate(new Date());
        child.setSchool(update.getSchool());
        child.setClassName(update.getClassName());
        child.setGender(update.getGender());
        child.setGrade(update.getGrade());
        child.setDiagnosis(update.getDiagnosis());
        child.setProvider(update.getProvider());
        child.setGroup(update.getGroup());
        child.setDropped(update.isDropped());

        if (isInCohort) {
            coachService.updateChild(children.get(0));
        } else {
            parentService.updateChild(child);
        }

        return child;
    }

    @GetMapping("/children/{username}/progress-report")
    public HistoricalProgressReport getChildProgressReport(@PathVariable("username") String username, HttpServletRequest request) throws Exception {

        SearchResponse response = analyticsService.historicalProgress(username);

        return HistoricalProgressReport.parse(response);
    }

    @GetMapping("/children/{username}/runner/{id}")
    public RunnerSummary getRunner(@PathVariable("username") String username, @PathVariable("id") String sessionId, HttpServletRequest request) throws Exception {

        SearchResponse response = analyticsService.runner(username, sessionId);
        RunnerSummary summary = SearchResultsMapper.getRunner(response, username, sessionId);

        return summary;
    }

    @GetMapping("/children/{username}/runners/{session_id}/tiers")
    public List<GraphResponse> getRunnerTierLevels(@PathVariable("username") String username, @PathVariable("session_id") String sessionId, HttpServletRequest request) throws Exception {

        SearchResponse response = analyticsService.tierLevels(username, sessionId);

        List<GraphResponse> levels = new ArrayList<>();

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

        for (SearchHit hit : response.getHits().getHits()) {

            Date start = df.parse((String) hit.getSourceAsMap().get("session_start"));
            Date at = df.parse((String) hit.getSourceAsMap().get("timestamp"));
            int tier = (int) hit.getSourceAsMap().get("Tier");

            long diffInMillies = at.getTime() - start.getTime();

            System.out.println(hit.getId());
            System.out.println(start);
            System.out.println(at);
            System.out.println(tier);

            long diff = TimeUnit.SECONDS.convert(diffInMillies, TimeUnit.MILLISECONDS);

            levels.add(new GraphResponse(diff, tier));
        }

        return levels;
    }

    @GetMapping("/children/{username}/stats")
    public SummaryReport getChildStats(@PathVariable("username") String username, HttpServletRequest request) throws Exception {

        SearchResponse response = analyticsService.summaryStats(username);

        return Instancio.create(SummaryReport.class);
    }

    @GetMapping("/children/{username}/perf-report")
    public PaginatedResponse<SessionSummary> getChildSessions(@PathVariable("username") String username, @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size, HttpServletRequest request) throws Exception {

        SearchResponse response = analyticsService.sessions(username, PageRequest.of(page, size));
        List<SessionSummary> sessions = new ArrayList<>();

        for (SearchHit hit : response.getHits().getHits()) {

            sessions.add(SearchResultsMapper.getSession(hit));
        }

        return new PaginatedResponse<>(sessions, response.getHits().getTotalHits().value);
    }

    @GetMapping("/children/{username}/state")
    public GameState getChildState(@PathVariable("username") String username, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

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

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        if (headsets.findById(headset.getId()).isPresent())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset ID already exists");

        Headset newHeadset = new Headset();
        newHeadset.setId(headset.getId());
        newHeadset.setManufacteDate(headset.getManufacteDate());

        return headsets.insert(newHeadset);
    }

    @PutMapping("/headsets/{id}")
    public Headset updateHeadset(@PathVariable String id, @RequestBody Headset headset, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        Headset old = headsets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset with that ID doesn't exist"));

        old.setManufacteDate(headset.getManufacteDate());

        return headsets.save(old);
    }

    @GetMapping("/headsets")
    public Page<Headset> listHeadsets(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        Pageable pageRequest = PageRequest.of(page, size);

        return headsets.findAll(pageRequest);
    }

    @GetMapping("/headsets/{id}")
    public Headset getHeadsets(@PathVariable String id, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        return headsets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset with that ID doesn't exist"));
    }

    @PostMapping("/headsets/{id}/assignment")
    public Headset assignHeadsets(@PathVariable String id, @RequestBody HeadsetAssignment assignment, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        Headset headset = headsets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset with that ID doesn't exist"));

        if (headset.getPlayer() != null && headset.getFirstUseTimestamp() != 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headset is already in use");

        Child child = parentService.getChildrenByUsername(Collections.singletonList(assignment.getUsername())).get(0);

        child.setHeadsetId(id);
        parentService.updateChild(child);

        headset.setPlayer(assignment.getUsername());
        return headsets.save(headset);
    }

    @PostMapping("/coaches/{coachId}/cohort/{cohortId}/import")
    public ResponseEntity<String> importUsers(@PathVariable String coachId, @PathVariable String cohortId, @RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);
        String bearerToken = jwtService.getAdminJwt();

        cohortService.processUsersCsv(file, coachId, cohortId, bearerToken);

        return ResponseEntity.accepted().body("CSV upload received");
    }

    @GetMapping("/imports")
    ResponseEntity<Page<ImportJob>> getImportJobs(HttpServletRequest request,
                                                  @RequestParam(required = false) String partialName,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) throws Exception {
        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        Pageable pageRequest = PageRequest.of(page, size, Direction.DESC, "createdDate");

        return ResponseEntity.ok(importJobService.getImportJobs(pageRequest));
    }
}