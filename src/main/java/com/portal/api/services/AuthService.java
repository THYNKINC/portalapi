package com.portal.api.services;

import com.portal.api.dto.request.CreateParentRequest;
import com.portal.api.dto.request.LoginRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Map;

@Service
public class AuthService {
    @Value("${app-client-id}")
    private String APP_CLIENT_ID;

    @Value("${group-name-user}")
    private String GROUP_NAME_USER;

    @Value("${user-pool-id}")
    private String USER_POOL_ID;

    private final CognitoIdentityProviderClient cognitoClient;

    public AuthService(CognitoIdentityProviderClient cognitoClient) {
        this.cognitoClient = cognitoClient;
    }


    public SignUpResponse registerUser(CreateParentRequest createParentRequest) {
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
                .groupName(GROUP_NAME_USER)
                .build();

        cognitoClient.adminAddUserToGroup(addUserToGroupRequest);

        return signUpResponse;
    }

    public String login(LoginRequest loginRequest) {

        InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .clientId(APP_CLIENT_ID)
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
}
