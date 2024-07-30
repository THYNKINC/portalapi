package com.portal.api.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.portal.api.dto.response.CognitiveSkillsResponse;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RunnerSummary extends SessionSummary {
	
	String responseTime;
	List<StarEarned> stars;
	int maxPower;
	int tierAvg;
	int tierMode;
	
	Accuracy accuracy;
	
	CognitiveSkillsResponse scores;
	
	Crystals crystals;
	
	Obstacles obstacles;
	
	final String type = "runner";
}
