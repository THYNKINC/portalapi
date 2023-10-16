package com.portal.api.model;

import lombok.Data;

@Data
public class Attempt {

	int missionId;
	long startTime;
	long endTime;
	int duration;
	int attemptNumber;
	String status;
	int bciMean;
	int bciStdDeviation;
	StarEarned[] stars;
	int maxPower;
	int tierAvg;
	Accuracy accuracy;
	int responseTime;
	CognitiveSkillsResponse scores;
	String[] ranks;
	String[] badges;
	Crystals crystals;
	Obstacles obstacles;
}
