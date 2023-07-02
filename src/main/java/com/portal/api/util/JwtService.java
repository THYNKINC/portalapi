package com.portal.api.util;

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

    @Autowired
    public JwtService(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
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

    public Jwt decodeJwtFromRequest(HttpServletRequest request, boolean adminRequired) throws Exception {
        String bearerToken = extractBearerToken(request);
        Jwt jwt = jwtDecoder.decode(bearerToken);
        
        if(adminRequired) {
        	List<String> groups = jwt.getClaim("cognito:groups");
        	if(!groups.contains(GROUP_NAME_ADMIN)) {
        		throw new Exception("You must be an admin to use this endpoint");
        	}
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
}
