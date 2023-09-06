package com.portal.api.model;

import lombok.Data;

@Data
public class SkillItem {

	double value = -1;
	int delta = -1;

	
	public SkillItem(int newValue, int oldValue) {
		
		super();
		
		if (newValue > 0)
			value = newValue / 100d;
		
		// avoid division by zero and irrelevant values
		if (oldValue <= 0 || newValue <= 0)
			return;
					
		delta = newValue * 100 / oldValue;
	}
}
