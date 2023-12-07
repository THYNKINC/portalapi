package com.portal.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
