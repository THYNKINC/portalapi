package com.portal.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Crystals {

	int collected;
	int missed;
	float pctCollected;
	int record;
}
