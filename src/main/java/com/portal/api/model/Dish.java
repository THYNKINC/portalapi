package com.portal.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Dish {

	String type;
	int selected;
	int rejected;
	int decoded;
	int duration;
	int selectTime;
	int tapTime;
	int gapTime;
	int decodeTime;
}
