package com.portal.api.model;

import java.util.OptionalDouble;
import java.util.stream.IntStream;

import com.portal.api.util.Constants;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImpulseControl {

	private String session;
	private double focus;
	private double impulse;
	
	public static ImpulseControl fromSkills(String attempt, CognitiveSkillsResponse skills, int missionNo) {
		
		double focus = 0;
		
		if (skills.getSustainedAttention() > 0)   		
			if (skills.getFocusedAttention() <= 0)
				focus = skills.getSustainedAttention();
			else if (skills.getSustainedAttention() <= 0)
				focus = skills.getSustainedAttention();
			else
				focus = (skills.getFocusedAttention() + skills.getSustainedAttention()) / 2;

		// apply weights to composite focus
		focus *= Constants.COMPOSITE_SCORE_WEIGHT[missionNo - 1];
		
		OptionalDouble impulse = IntStream
				.of(
						skills.getCognitiveInhibition(),
						skills.getBehavioralInhibition(),
						skills.getNoveltyInhibition(),
						skills.getMotivationalInhibition(),
						skills.getInterferenceControl())
				.filter(i -> i >= 0)
				.average();
		
		return new ImpulseControl(attempt, focus, impulse.orElse(-1));
	}
}
