package com.portal.api.services;

import com.portal.api.dto.request.CreateCoachRequest;
import com.portal.api.dto.request.CreateParentRequest;
import com.portal.api.dto.request.LoginRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Map;

@Service
public class AuthService {

    public static final String EMAIL = "email";
    public static final String GIVEN_NAME = "given_name";
    public static final String FAMILY_NAME = "family_name";
    @Value("${app-client-id}")
    private String APP_CLIENT_ID;

    @Value("${group-name-user}")
    private String GROUP_NAME_USER;

    @Value("${group-name-delegate}")
    private String GROUP_NAME_DELEGATE;

    @Value("${user-pool-id}")
    private String USER_POOL_ID;

    private final CognitoIdentityProviderClient cognitoClient;

    public AuthService(CognitoIdentityProviderClient cognitoClient) {
        this.cognitoClient = cognitoClient;
    }


    public SignUpResponse registerUser(CreateParentRequest createParentRequest) {

        SignUpResponse signUpResponse = signUpUser(
                createParentRequest.getEmail(), createParentRequest.getPassword(),
                createParentRequest.getFirstName(), createParentRequest.getLastName()
        );

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
                        Map.of("USERNAME", loginRequest.getUsername(), "PASSWORD", loginRequest.getPassword())
                )
                .build();

        InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);
        AuthenticationResultType authResult = authResponse.authenticationResult();

        return authResult.idToken();
    }

    public SignUpResponse registerCoach(CreateCoachRequest createCoachRequest) {

        SignUpResponse signUpResponse = signUpUser(
                createCoachRequest.getEmail(), createCoachRequest.getPassword(),
                createCoachRequest.getFirstName(), createCoachRequest.getLastName()
        );

        // Add user to delegate group
        AdminAddUserToGroupRequest addUserToGroupRequest = AdminAddUserToGroupRequest.builder()
                .userPoolId(USER_POOL_ID)
                .username(createCoachRequest.getEmail())
                .groupName(GROUP_NAME_DELEGATE)
                .build();

        cognitoClient.adminAddUserToGroup(addUserToGroupRequest);

        return signUpResponse;
    }

    private SignUpResponse signUpUser(String email, String password, String firstName, String lastName) {
        SignUpRequest signUpRequest = SignUpRequest.builder()
                .clientId(APP_CLIENT_ID)
                .username(email)
                .password(password)
                .userAttributes(
                        AttributeType.builder().name(EMAIL).value(email).build(),
                        AttributeType.builder().name(GIVEN_NAME).value(firstName).build(),
                        AttributeType.builder().name(FAMILY_NAME).value(lastName).build()
                )
                .build();

        return cognitoClient.signUp(signUpRequest);
    }
}
