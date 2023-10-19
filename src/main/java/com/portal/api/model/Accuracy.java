package com.portal.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Accuracy {

	float correctRejected;
	float incorrectRejected;
	float correctSelected;
	float incorrectSelected;
	int opportunities;
}
