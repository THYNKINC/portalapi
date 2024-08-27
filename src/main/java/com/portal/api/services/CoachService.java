package com.portal.api.services;

import com.portal.api.dto.request.CreateParentRequest;
import com.portal.api.model.Delegate;
import com.portal.api.repositories.DelegateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;

import java.util.ArrayList;
import java.util.Date;


@Service
public class CoachService {

    private final DelegateRepository delegateRepository;

    private final AuthService authService;

    public CoachService(DelegateRepository delegateRepository, AuthService authService) {
        this.delegateRepository = delegateRepository;
        this.authService = authService;
    }

    public Page<Delegate> getCoaches(Pageable pageable) {
        return delegateRepository.findByType("coach", pageable);
    }

    public Delegate createCoach(CreateParentRequest createCoachRequest) {

        SignUpResponse signUpResponse = authService.registerUser(createCoachRequest);

        Delegate coach = new Delegate();

        coach.setCreatedDate(new Date());
        coach.setChildren(new ArrayList<>());
        coach.setEmail(createCoachRequest.getEmail());
        coach.setFirstName(createCoachRequest.getFirstName());
        coach.setLastName(createCoachRequest.getLastName());
        coach.setUsername(signUpResponse.userSub());
        coach.setSalutation(createCoachRequest.getSalutation());
        coach.setType("coach");

        delegateRepository.save(coach);

        return coach;
    }
}
