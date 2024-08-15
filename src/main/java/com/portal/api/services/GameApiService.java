package com.portal.api.services;

import com.portal.api.clients.GameApiClient;
import com.portal.api.dto.request.CreateChildRequest;
import com.portal.api.dto.request.CreateUserRequest;
import com.portal.api.dto.response.RegisterUserStatus;
import com.portal.api.model.PortalUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameApiService {

    private final GameApiClient gameApiClient;

    public GameApiService(GameApiClient gameApiClient) {
        this.gameApiClient = gameApiClient;
    }

    public void createNewUserFromChild(CreateChildRequest createChildRequest, PortalUser user, String adminJwt) {

        CreateUserRequest createUserRequest = new CreateUserRequest();
        createUserRequest.setEmail(user.getEmail());
        createUserRequest.setFirstName(createChildRequest.getFirstName());
        createUserRequest.setLastName(createChildRequest.getLastName());
        createUserRequest.setParent(user.getUsername());
        createUserRequest.setPassword(createChildRequest.getPassword());
        createUserRequest.setUsername(createChildRequest.getUsername());

        gameApiClient.createNewUser(createUserRequest, adminJwt);
    }

    public List<RegisterUserStatus> registerMultipleUsers(List<CreateUserRequest> createUserRequests, String adminJwt) {
        return gameApiClient.registerMultipleUsers(createUserRequests, adminJwt);
    }
}
