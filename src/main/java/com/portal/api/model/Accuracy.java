package com.portal.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Accuracy {

	int correctRejected;
	int incorrectRejected;
	int correctSelected;
	int incorrectSelected;
	int impulses;
	int opportunities;
}
