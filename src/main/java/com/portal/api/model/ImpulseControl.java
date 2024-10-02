package com.portal.api.model;

import java.util.OptionalDouble;
import java.util.stream.IntStream;

import com.portal.api.dto.response.CognitiveSkillsResponse;
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
		int fa = skills.getFocusedAttention();
		int sa = skills.getSustainedAttention();
		
		focus = (fa > 0 && sa > 0) ? (fa + sa) / 2 : Math.max(fa, sa);
		focus = Math.max(focus, 0);

		// apply weights to composite focus
		focus *= Constants.COMPOSITE_SCORE_WEIGHT[missionNo - 1];
		
		OptionalDouble impulse = IntStream
				.of(
						skills.getSelectiveAttention(),
						skills.getAlternatingAttention(),
						skills.getBehavioralInhibition(),
						skills.getNoveltyInhibition(),
						skills.getMotivationalInhibition(),
						skills.getInterferenceControl())
				.filter(i -> i >= 0)
				.average();
		
		return new ImpulseControl(attempt, focus, impulse.orElse(-1));
	}
}
