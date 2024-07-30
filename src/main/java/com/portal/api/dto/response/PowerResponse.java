package com.portal.api.dto.response;

import java.util.List;

import lombok.Data;

@Data
public class PowerResponse {

	private List<GraphResponse> data;
	private int[] thresholds;
	private int[] thresholdPercentages;
}
