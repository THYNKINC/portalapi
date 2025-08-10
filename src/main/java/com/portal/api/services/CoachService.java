package com.portal.api.services;

import com.portal.api.dto.request.CreateCoachRequest;
import com.portal.api.dto.request.UpdateCoachRequest;
import com.portal.api.exception.ResourceNotFoundException;
import com.portal.api.model.Child;
import com.portal.api.model.Delegate;
import com.portal.api.repositories.DelegateRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;

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

    private final MongoTemplate mongoTemplate;


    public CoachService(DelegateRepository delegateRepository, AuthService authService, MongoTemplate mongoTemplate) {
        this.delegateRepository = delegateRepository;
        this.authService = authService;
        this.mongoTemplate = mongoTemplate;
    }

    public Page<Delegate> getCoaches(Pageable pageable) {
        return delegateRepository.findByType(COACH, pageable);
    }

    public Delegate createCoach(CreateCoachRequest createCoachRequest) {

        AdminCreateUserResponse createUserResponse = authService.registerCoach(createCoachRequest);
        String generatedUserName = createUserResponse.user().attributes().stream()
                .filter(attribute -> attribute.name().equals("sub"))
                .findFirst()
                .map(AttributeType::value)
                .orElse(createUserResponse.user().username());

        Delegate coach = new Delegate();

        coach.setCreatedDate(new Date());
        coach.setChildren(new ArrayList<>());
        coach.setEmail(createCoachRequest.getEmail());
        coach.setFirstName(createCoachRequest.getFirstName());
        coach.setLastName(createCoachRequest.getLastName());
        coach.setUsername(generatedUserName);
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
        updateChild(child, null);
    }

    public void updateChild(Child child, String newCoachId) {
        Delegate currentCoach = getCoachByChildName(child.getUsername());

        if (StringUtils.isBlank(newCoachId) || currentCoach.getUsername().equals(newCoachId)) {
            List<Child> children = currentCoach.getChildren().stream()
                    .map(e -> e.getUsername().equals(child.getUsername()) ? child : e)
                    .toList();

            currentCoach.setChildren(children);
            delegateRepository.save(currentCoach);
        } else if (StringUtils.isNotBlank(newCoachId)) {
            Delegate newCoach = getCoach(newCoachId);

            newCoach.addChild(child);
            delegateRepository.save(newCoach);

            currentCoach.setChildren(
                    currentCoach
                            .getChildren()
                            .stream()
                            .filter(it -> !it.getUsername().equals(child.getUsername()))
                            .collect(Collectors.toList())
            );
            delegateRepository.save(currentCoach);
        }


    }

    public List<Child> getAllChildren() {
        AggregationOperation replaceRootOperation = Aggregation.replaceRoot().withValueOf("$children");
        AggregationOperation unwindOperation = Aggregation.unwind("children");

        Aggregation aggregation = Aggregation.newAggregation(
                unwindOperation,
                replaceRootOperation
        );

        return mongoTemplate.aggregate(aggregation, "delegate", Child.class).getMappedResults();
    }
}
