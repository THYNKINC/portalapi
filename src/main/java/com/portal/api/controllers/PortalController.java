package com.portal.api.controllers;

import java.util.ArrayList;
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
import com.portal.api.model.LoginRequest;
import com.portal.api.model.Parent;
import com.portal.api.model.ProgressResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.api.model.Child;
import com.portal.api.util.HttpService;
import com.portal.api.util.JwtService;
import com.portal.api.util.MongoService;

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

    @Autowired
    public PortalController(
    		JwtService jwtService,
    		MongoService mongoService) {
        this.jwtService = jwtService;
        this.mongoService = mongoService;
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

    @PostMapping("/child")
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
    
    @GetMapping("/child/{username}/progress")
    public ProgressResponse childProgress(@PathVariable("username") String id, HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false);
    	
    	ProgressResponse progressResponse = new ProgressResponse();
    	progressResponse.setAbandonedAttempts(2);
    	progressResponse.setCompletedSessions(4);
    	progressResponse.setMissionsCompleted(3);
    	progressResponse.setSessionsCompleted(1);
    	
    	return progressResponse;
    }
    
    @GetMapping("/me")
    public Parent getParent(HttpServletRequest request) throws Exception {
    	Jwt jwt = jwtService.decodeJwtFromRequest(request, false);
    	return mongoService.getParent(jwt.getClaim("cognito:username"));		
    }

}