package com.portal.api.model;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class PVTSummary extends SessionSummary {

	@Builder.Default
	final String type = "pvt";
}
