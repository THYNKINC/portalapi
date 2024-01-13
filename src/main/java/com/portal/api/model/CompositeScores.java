package com.portal.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompositeScores {

	Stats focus;
	Stats impulse;
}
