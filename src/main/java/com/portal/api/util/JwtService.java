package com.portal.api.util;

import com.portal.api.model.Child;
import com.portal.api.model.PortalUser;
import com.portal.api.model.Role;
import com.portal.api.repositories.CoachRepository;
import com.portal.api.repositories.DelegateRepository;
import com.portal.api.services.ParentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {

	private final CoachRepository coachRepository;
	@Value("${admin-username}")
	private String ADMIN_USERNAME;
	
	@Value("${admin-password}")
	private String ADMIN_PASSWORD;
	
	@Value("${games-app-client-id}")
	private String GAMES_APP_CLIENT_ID;
	
	@Value("${group-name-admin}")
	private String GROUP_NAME_ADMIN;
	
	@Value("${group-name-delegate}")
	private String GROUP_NAME_DELEGATE;
	
    private final JwtDecoder jwtDecoder;
    
    private final ParentService parentService;
    
    private final DelegateRepository delegates;

    @Autowired
    public JwtService(JwtDecoder jwtDecoder, ParentService parentService, DelegateRepository delegates, CoachRepository coachRepository) {
        this.jwtDecoder = jwtDecoder;
        this.parentService = parentService;
		this.delegates = delegates;
		this.coachRepository = coachRepository;
	}
    
    public String getAdminJwt() {

    	CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .clientId(GAMES_APP_CLIENT_ID)	//COGNITO_APP_CLIENT_ID            
                .authParameters(
                        Map.of(
                                "USERNAME", ADMIN_USERNAME,
                                "PASSWORD", ADMIN_PASSWORD
                        )
                )
                .build();

        InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);
        
        AuthenticationResultType authResult = authResponse.authenticationResult();
        
        return authResult.idToken();
    }

    // TODO review for admins, cause we don't have a admin type right now, it's just parents and delegates
    public PortalUser decodeJwtFromRequest(HttpServletRequest request, boolean adminRequired, String child) throws Exception {
        
    	String bearerToken = extractBearerToken(request);
        
    	Jwt jwt = jwtDecoder.decode(bearerToken);
    	
    	List<String> groups = jwt.getClaim("cognito:groups");

        // How does the coach fit in here?
    	Role role = groups.contains(GROUP_NAME_DELEGATE) ? Role.delegate : 
    		groups.contains(GROUP_NAME_ADMIN) ? Role.admin : Role.parent;
    	
    	PortalUser user = switch (role) {
            case parent, admin -> parentService.getParent(jwt.getClaim("cognito:username"));
            case delegate -> delegates.findById(jwt.getClaim("cognito:username")).get();
            case coach -> coachRepository.findById(jwt.getClaim("cognito:username")).get();
        };


        if(role != Role.admin) {
        	
        	if(adminRequired) {
        		throw new Exception("You must be an admin to use this endpoint");
        	}
        	
        	if(child != null && !childFound(user, child)) {
        		throw new Exception("This is not your child");
        	}
        }
        
        return user;
    }

    public String extractBearerToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }
    
    private boolean childFound(PortalUser user, String child) {
    	
    	List<Child> children = user.getChildren();
    	
    	for (Child c : children) {
            if (c.getUsername().equals(child)) {
                return true;
            }
        }
    	
    	return false;
    }
}
