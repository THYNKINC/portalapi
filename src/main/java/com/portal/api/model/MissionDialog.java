package com.portal.api.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@Document(collection = "missionDialogs")
public class MissionDialog {

    @Id
    private String missionId;

    @NotBlank(message = "Game goals cannot be empty")
    private String gameGoals;

    @NotBlank(message = "Cognitive skills goals cannot be empty")
    private String cognitiveSkillsGoals;

    @NotBlank(message = "Behavioral changes for child cannot be empty")
    private String behavioralChangesChild;

    @NotBlank(message = "Behavioral changes for adult cannot be empty")
    private String behavioralChangesAdult;

    @NotBlank(message = "Failed runner cannot be empty")
    private String failedRunner;

    @NotBlank(message = "Passed runner 1 star cannot be empty")
    private String passedRunner1Star;

    @NotBlank(message = "Passed runner 2 star cannot be empty")
    private String passedRunner2Star;

    @NotBlank(message = "Passed runner 3 star cannot be empty")
    private String passedRunner3Star;

    @NotBlank(message = "What's next failed runner cannot be empty")
    private String whatsNextFailedRunner;

    @NotBlank(message = "Failed runner no attempt cannot be empty")
    private String failedRunnerNoAttempt;

    @NotBlank(message = "Failed attempt cannot be empty")
    private String failedAttempt;

    @NotBlank(message = "Passed attempt cannot be empty")
    private String passedAttempt;

    @NotBlank(message = "Mission number focus cannot be empty")
    private String missionNumberFocus;

    @NotBlank(message = "Focus definition popup cannot be empty")
    private String focusDefinitionPopup;

    @NotBlank(message = "Impuls control definition cannot be empty")
    private String impulsControlDefinition;
}
