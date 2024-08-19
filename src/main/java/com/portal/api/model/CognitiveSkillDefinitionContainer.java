package com.portal.api.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@Document(collection = "cognitiveSkillDefinitions")
public class CognitiveSkillDefinitionContainer {

    @Id
    private String id;

    @NotNull(message = "Focused attention cannot be null")
    @Valid
    private CognitiveSkillDefinition focusedAttention;

    @NotNull(message = "Sustained attention cannot be null")
    @Valid
    private CognitiveSkillDefinition sustainedAttention;

    @NotNull(message = "Cognitive inhibition cannot be null")
    @Valid
    private CognitiveSkillDefinition cognitiveInhibition;

    @NotNull(message = "Behavioral inhibition cannot be null")
    @Valid
    private CognitiveSkillDefinition behavioralInhibition;

    @NotNull(message = "Selective attention cannot be null")
    @Valid
    private CognitiveSkillDefinition selectiveAttention;

    @NotNull(message = "Alternating attention cannot be null")
    @Valid
    private CognitiveSkillDefinition alternatingAttention;

    @NotNull(message = "Divided attention cannot be null")
    @Valid
    private CognitiveSkillDefinition dividedAttention;

    @NotNull(message = "Interference control cannot be null")
    @Valid
    private CognitiveSkillDefinition interferenceControl;

    @NotNull(message = "Motivational inhibition cannot be null")
    @Valid
    private CognitiveSkillDefinition motivationalInhibition;

    @NotNull(message = "Novelty inhibition cannot be null")
    @Valid
    private CognitiveSkillDefinition noveltyInhibition;

    @NotNull(message = "Delay of gratification cannot be null")
    @Valid
    private CognitiveSkillDefinition delayOfGratification;

    @NotNull(message = "Inner voice cannot be null")
    @Valid
    private CognitiveSkillDefinition innerVoice;

    @NotNull(message = "Self-regulation cannot be null")
    @Valid
    private CognitiveSkillDefinition selfRegulation;
}
