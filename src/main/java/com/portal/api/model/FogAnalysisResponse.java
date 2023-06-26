package com.portal.api.model;

import lombok.Data;

@Data
public class FogAnalysisResponse {

	private String performance;
	private String attention;
	private int frozenDishes;
	private int decodedMolecules;
	
}