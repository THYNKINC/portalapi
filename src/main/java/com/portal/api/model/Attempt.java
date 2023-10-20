package com.portal.api.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Attempt {

	boolean completed;
	int missionId;
	long startTime;
	long endTime;
	int duration;
	int attemptNumber;
	String status;
	int bciMean;
	int bciStdDeviation;
	List<StarEarned> stars;
	int maxPower;
	int tierAvg;
	Accuracy accuracy;
	int responseTime;
	CognitiveSkillsResponse scores;
	String[] ranks;
	String[] badges;
	Crystals crystals;
	Obstacles obstacles;
	String type;
}
