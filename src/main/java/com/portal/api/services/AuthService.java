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
    public static final String EMAIL_VERIFIED = "email_verified";

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

    public AdminCreateUserResponse registerCoach(CreateCoachRequest createCoachRequest) {

        AdminCreateUserResponse createUserResponse = adminCreateUser(
                createCoachRequest.getEmail(),
                createCoachRequest.getPassword(),
                createCoachRequest.getFirstName(),
                createCoachRequest.getLastName()
        );

        // Add user to delegate group
        AdminAddUserToGroupRequest addUserToGroupRequest = AdminAddUserToGroupRequest.builder()
                .userPoolId(USER_POOL_ID)
                .username(createCoachRequest.getEmail())
                .groupName(GROUP_NAME_DELEGATE)
                .build();

        cognitoClient.adminAddUserToGroup(addUserToGroupRequest);

        return createUserResponse;
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

    private AdminCreateUserResponse adminCreateUser(String email, String password, String firstName, String lastName) {
        AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                .userPoolId(USER_POOL_ID)
                .username(email)
                // we're just setting something, we're going to change it to something else before we finish creating the user
                .temporaryPassword("TemporaryPassword123!")
                .messageAction(MessageActionType.SUPPRESS)
                .userAttributes(
                        AttributeType.builder().name(EMAIL).value(email).build(),
                        AttributeType.builder().name(GIVEN_NAME).value(firstName).build(),
                        AttributeType.builder().name(FAMILY_NAME).value(lastName).build(),
                        // since we're admin creating the user, we'll just set the email to verified manually or it will never be verified
                        AttributeType.builder().name(EMAIL_VERIFIED).value("true").build()
                )
                .build();

        AdminCreateUserResponse createUserResponse = cognitoClient.adminCreateUser(createUserRequest);

        AdminSetUserPasswordRequest setUserPasswordRequest = AdminSetUserPasswordRequest.builder()
                .userPoolId(USER_POOL_ID)
                .username(email)
                .password(password)
                .permanent(true)
                .build();

        cognitoClient.adminSetUserPassword(setUserPasswordRequest);

        return createUserResponse;
    }
}
