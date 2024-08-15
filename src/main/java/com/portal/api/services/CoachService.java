package com.portal.api.services;

import com.portal.api.dto.request.CreateParentRequest;
import com.portal.api.model.Coach;
import com.portal.api.repositories.CoachRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;

import java.util.ArrayList;
import java.util.Date;


@Service
public class CoachService {

    private final CoachRepository coachRepository;

    private final AuthService authService;

    public CoachService(CoachRepository coachRepository, AuthService authService) {
        this.coachRepository = coachRepository;
        this.authService = authService;
    }

    public Page<Coach> getCoaches(Pageable pageable) {
        return coachRepository.findAll(pageable);
    }

    public Coach createCoach(CreateParentRequest createCoachRequest) {

        SignUpResponse signUpResponse = authService.registerUser(createCoachRequest);

        Coach coach = new Coach();

        coach.setCreatedDate(new Date());
        coach.setChildren(new ArrayList<>());
        coach.setEmail(createCoachRequest.getEmail());
        coach.setFirstName(createCoachRequest.getFirstName());
        coach.setLastName(createCoachRequest.getLastName());
        coach.setUsername(signUpResponse.userSub());
        coach.setSalutation(createCoachRequest.getSalutation());

        coachRepository.save(coach);

        return coach;
    }
}
