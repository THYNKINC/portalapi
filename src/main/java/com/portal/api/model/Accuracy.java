package com.portal.api.model;

import lombok.Data;

@Data
public class Accuracy {

	float correctRejected;
	float incorrectRejected;
	float correctSelected;
	float incorrectSelected;
	int botsSeen;
}
