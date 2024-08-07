package com.portal.api.clients;

import com.portal.api.exception.GameApiException;
import com.portal.api.dto.request.CreateUserRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class GameApiClient {

    private final RestTemplate gameApiRestClient;

    public GameApiClient(RestTemplate gameApiRestClient) {
        this.gameApiRestClient = gameApiRestClient;
    }

    public void createNewUser(CreateUserRequest createUserRequest, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);

        HttpEntity<CreateUserRequest> entity = new HttpEntity<>(createUserRequest, headers);
        try {
            gameApiRestClient.exchange("/games/users", HttpMethod.POST, entity, Void.class);
        } catch (RestClientException exception) {
            throw new GameApiException(exception.getMessage());
        }
    }
}
