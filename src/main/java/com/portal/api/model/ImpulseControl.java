package com.portal.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImpulseControl {

	private String session;
	private double focus;
	private double impulse;
	
	
}
