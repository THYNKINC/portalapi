package com.portal.api.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@AllArgsConstructor
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
