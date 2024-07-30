package com.portal.api.model;

import java.util.List;

import com.portal.api.dto.response.CognitiveSkillsResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
// TODO delete
public class Attempt {

	String id;
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
	String responseTime;
	CognitiveSkillsResponse scores;
	String[] ranks;
	String[] badges;
	Crystals crystals;
	Obstacles obstacles;
	String type;
}
