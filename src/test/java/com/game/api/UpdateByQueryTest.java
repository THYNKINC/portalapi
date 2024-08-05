package com.game.api;

import com.portal.api.model.RunnerSummary;
import com.portal.api.model.SessionSummary;
import com.portal.api.scheduled.SessionsComputer;

public class UpdateByQueryTest {

	public static void main(String[] args) {
		
		SessionsComputer sc = new SessionsComputer();
		
		SessionSummary session = RunnerSummary.builder()
			.id("test")
			.completed(false)
			.status("FAIL")
			.build();
		
		System.out.println(sc.buildUpdateQuery(session));
	}
}
