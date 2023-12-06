package com.portal.api.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class TransferenceSummary extends SessionSummary {
	
	int selectAvg;
	int selectStdDev;
	int decodeAvg;
	int decodeStdDev;
	int tapAvg;
	int tapStdDev;
	int gapAvg;
	int gapStdDev;
	List<Dish> dishes;
	int target;
	int decoded;
	int pctDecoded;
	
	@Builder.Default
	final String type = "transference";
}
