package com.portal.api.dto.response;

import com.portal.api.model.SkillItem;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CognitiveSkillsProgressResponse {

	SkillItem focusedAttention;
	SkillItem sustainedAttention;
	SkillItem cognitiveInhibition;
	SkillItem behavioralInhibition;
	SkillItem selectiveAttention;
	SkillItem alternatingAttention;
	SkillItem dividedAttention;
	SkillItem interferenceControl;
	SkillItem motivationalInhibition;
	SkillItem noveltyInhibition;
	SkillItem delayOfGratification;
	SkillItem innerVoice;
	SkillItem selfRegulation;
}
