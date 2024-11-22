package com.portal.api.services;

import com.portal.api.dto.request.CreateCoachRequest;
import com.portal.api.dto.request.UpdateCoachRequest;
import com.portal.api.exception.ResourceNotFoundException;
import com.portal.api.model.Child;
import com.portal.api.model.Delegate;
import com.portal.api.model.Parent;
import com.portal.api.model.PortalUser;
import com.portal.api.repositories.DelegateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class CoachService {

    public static final String COACH = "coach";

    private final DelegateRepository delegateRepository;

    private final AuthService authService;

    public CoachService(DelegateRepository delegateRepository, AuthService authService) {
        this.delegateRepository = delegateRepository;
        this.authService = authService;
    }

    public Page<Delegate> getCoaches(Pageable pageable) {
        return delegateRepository.findByType(COACH, pageable);
    }

    public Delegate createCoach(CreateCoachRequest createCoachRequest) {

        SignUpResponse signUpResponse = authService.registerCoach(createCoachRequest);

        Delegate coach = new Delegate();

        coach.setCreatedDate(new Date());
        coach.setChildren(new ArrayList<>());
        coach.setEmail(createCoachRequest.getEmail());
        coach.setFirstName(createCoachRequest.getFirstName());
        coach.setLastName(createCoachRequest.getLastName());
        coach.setUsername(signUpResponse.userSub());
        coach.setSalutation(createCoachRequest.getSalutation());
        coach.setType(COACH);

        delegateRepository.save(coach);

        return coach;
    }

    public Delegate getCoach(String username) {
        Optional<Delegate> coachOptional = delegateRepository.findById(username);
        if (coachOptional.isEmpty()) {
            throw new ResourceNotFoundException("Coach not found");
        }

        return coachOptional.get();
    }

    public Delegate update(UpdateCoachRequest updateCoachRequest, String username) {
        Delegate coach = getCoach(username);
        coach.setFirstName(updateCoachRequest.getFirstName());
        coach.setLastName(updateCoachRequest.getLastName());
        coach.setSalutation(updateCoachRequest.getSalutation());

        delegateRepository.save(coach);

        return coach;
    }

    public Delegate getCoachByChildName(String username) {
        return delegateRepository.findOneByChildrenUsername(username);
    }

    public void updateChild(Child child) {
        Delegate coach = getCoachByChildName(child.getUsername());

        List<Child> children = coach.getChildren().stream()
                .map(e -> e.getUsername().equals(child.getUsername()) ? child : e)
                .collect(Collectors.toList());

        coach.setChildren(children);
        delegateRepository.save(coach);
    }
}
