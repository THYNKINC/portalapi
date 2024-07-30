package com.portal.api.dto.response;

import java.util.List;

import com.portal.api.model.StartEnd;
import lombok.Data;

@Data
public class FogAnalysisResponse {

	private int frozenDishes;
	private int decodedMolecules;
	private List<StartEnd> dishes;
	private int targetDecodes;
	private boolean pass;
}