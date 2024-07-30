package com.portal.api.dto.request;

import java.time.LocalDate;

import lombok.Data;

@Data
public class CreateHeadsetRequest {

	private String id;
	LocalDate manufacteDate;
}
