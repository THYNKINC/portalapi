package com.portal.api.clients;

import com.portal.api.dto.request.CreateCohortUserRequest;
import com.portal.api.dto.request.CreateUserRequest;
import com.portal.api.dto.response.RegisterUserStatus;
import com.portal.api.exception.GameApiException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class GameApiClient {

    public static final String BASE_PATH = "/games/users";
    private final RestTemplate gameApiRestClient;



    public GameApiClient(RestTemplate gameApiRestClient) {
        this.gameApiRestClient = gameApiRestClient;
    }

    public void createNewUser(CreateUserRequest createUserRequest, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);

        HttpEntity<CreateUserRequest> entity = new HttpEntity<>(createUserRequest, headers);
        try {
            gameApiRestClient.exchange(BASE_PATH, HttpMethod.POST, entity, Void.class);
        } catch (RestClientException exception) {
            throw new GameApiException(exception.getMessage());
        }
    }

    public void createNewCohortUser(CreateCohortUserRequest createUserRequest, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);

        HttpEntity<CreateCohortUserRequest> entity = new HttpEntity<>(createUserRequest, headers);
        try {
            gameApiRestClient.exchange(BASE_PATH + "/cohort/add", HttpMethod.POST, entity, Void.class);
        } catch (RestClientException exception) {
            throw new GameApiException(exception.getMessage());
        }
    }

    public List<RegisterUserStatus> registerMultipleUsers(List<CreateCohortUserRequest> createUserRequestList, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);

        HttpEntity<List<CreateCohortUserRequest>> entity = new HttpEntity<>(createUserRequestList, headers);
        try {
            var result = gameApiRestClient.exchange(BASE_PATH + "/import", HttpMethod.POST, entity, new ParameterizedTypeReference<List<RegisterUserStatus>>() {});

            return result.getBody();

        } catch (RestClientException exception) {
            throw new GameApiException(exception.getMessage());
        }
    }
}
