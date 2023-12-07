package com.portal.api.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
public class RunnerSummary extends SessionSummary {
	
	String responseTime;
	List<StarEarned> stars;
	int maxPower;
	int tierAvg;
	Accuracy accuracy;
	CognitiveSkillsResponse scores;
	String[] ranks;
	String[] badges;
	Crystals crystals;
	Obstacles obstacles;
	
	@Builder.Default
	final String type = "runner";
}
