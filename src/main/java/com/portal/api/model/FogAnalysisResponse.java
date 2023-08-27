package com.portal.api.model;

import java.util.List;

import lombok.Data;

@Data
public class FogAnalysisResponse {

	private int frozenDishes;
	private int decodedMolecules;
	private List<StartEnd> dishes;
	private int targetDecodes;
	private boolean pass;
}