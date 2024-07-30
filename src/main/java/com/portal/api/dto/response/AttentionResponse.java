package com.portal.api.dto.response;

import java.util.List;

import lombok.Data;

@Data
public class AttentionResponse {

	private List<GraphResponse> attention;
	private double averageAttention;
	private double averageAttentionRequired;
	
}