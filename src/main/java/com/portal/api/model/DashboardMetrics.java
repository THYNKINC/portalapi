package com.portal.api.model;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardMetrics {

	int totalUsers;
	String totalPlaytime;
	Map<String, Integer> sessions;
	Map<String, Integer> missions;
	Map<String, Integer> attempts;
	Map<String, Integer> abandons;
	Map<String, Integer> power;
}
