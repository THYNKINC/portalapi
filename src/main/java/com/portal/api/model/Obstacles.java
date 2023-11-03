package com.portal.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Obstacles {

	int avoided;
	int collided;
	int pctAvoided;
}
