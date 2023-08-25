package com.portal.api.model;

import lombok.Data;

@Data
public class SkillItem {

	double value = -1;
	int delta = 0;

	
	public SkillItem(int newValue, int oldValue) {
		
		super();
		
		// avoid division by zero and irrelevant values
		if (oldValue <= 0)
			return;
					
		value = newValue / 100d;
		delta = newValue * 100 / oldValue;
	}
}
