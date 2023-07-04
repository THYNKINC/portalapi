package com.portal.api.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

import com.portal.api.model.Child;
import com.portal.api.model.Parent;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

@Service
public class JwtService {
	
	@Value("${admin-username}")
	private String ADMIN_USERNAME;
	
	@Value("${admin-password}")
	private String ADMIN_PASSWORD;
	
	@Value("${games-app-client-id}")
	private String GAMES_APP_CLIENT_ID;
	
	@Value("${group-name-admin}")
	private String GROUP_NAME_ADMIN;
	
    private final JwtDecoder jwtDecoder;
    
    private final MongoService mongoService;

    @Autowired
    public JwtService(JwtDecoder jwtDecoder, MongoService mongoService) {
        this.jwtDecoder = jwtDecoder;
        this.mongoService = mongoService;
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

    public Jwt decodeJwtFromRequest(HttpServletRequest request, boolean adminRequired, String child) throws Exception {
        String bearerToken = extractBearerToken(request);
        Jwt jwt = jwtDecoder.decode(bearerToken);
        
        List<String> groups = jwt.getClaim("cognito:groups");
        if(!groups.contains(GROUP_NAME_ADMIN)) {
        	if(adminRequired) {
        		throw new Exception("You must be an admin to use this endpoint");
        	}
        	if(child != null && !childFound(jwt.getClaim("cognito:username"), child)) {
        		throw new Exception("This is not your child");
        	}
        	//return parent.getChildren();
        }
        
        
        return jwt;
    }

    public String extractBearerToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }
    
    private boolean childFound(String parent, String child) {
    	List<Child> children = mongoService.getParent(parent).getChildren();
    	for (Child c : children) {
            if (c.getUsername().equals(child)) {
                return true;
            }
        }
    	return false;
    }
}
