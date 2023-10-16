package com.portal.api.model;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "headset")
public class Headset {

	@Id
	private String id;
	
	LocalDate manufacteDate;
	String player;
	long firstUseTimestamp;
	long lastUseTimestamp;
}
