package com.portal.api.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferenceSummary {

	long startDate;
	long endDate;
	int duration;
	String status;
	int bciAvg;
	int selectAvg;
	int selectStdDev;
	int decodeAvg;
	int decodeStdDev;
	int tapAvg;
	int tapStdDev;
	int gapAvg;
	int gapStdDev;
	List<Dish> dishes;
	boolean completed;
	int target;
	int decoded;
	int pctDecoded;
}
