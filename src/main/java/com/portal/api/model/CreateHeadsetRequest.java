package com.portal.api.model;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;

import lombok.Data;

@Data
public class CreateHeadsetRequest {

	private String id;
	LocalDate manufacteDate;
}
