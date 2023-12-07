package com.portal.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Accuracy {

	int correctRejected;
	int incorrectRejected;
	int correctSelected;
	int incorrectSelected;
	int impulses;
	int opportunities;
}
