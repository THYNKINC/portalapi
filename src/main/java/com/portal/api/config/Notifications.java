package com.portal.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties("notifications")
@Data
public class Notifications {

	String mandrillUrl;
	String mandrillApiKey;
	String fromEmail;
	String fromName;
	String bccEmail;
	
	String tplIdleTwoDaysReminder;
	String tplIdleFiveDaysReminder;
}
