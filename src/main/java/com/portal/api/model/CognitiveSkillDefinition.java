package com.portal.api.model;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class CognitiveSkillDefinition {

    @NotBlank(message = "Definition cannot be empty")
    private String definition;

    @NotBlank(message = "Cognitive skill definition cannot be empty")
    private String cognitiveSkillDefinition;
}
