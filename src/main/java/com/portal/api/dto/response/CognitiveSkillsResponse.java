package com.portal.api.dto.response;

import lombok.Data;

@Data
public class CognitiveSkillsResponse {

	int focusedAttention = -1;
	int sustainedAttention = -1;
	int cognitiveInhibition = -1;
	int behavioralInhibition = -1;
	int selectiveAttention = -1;
	int alternatingAttention = -1;
	int dividedAttention = -1;
	int interferenceControl = -1;
	int motivationalInhibition = -1;
	int noveltyInhibition = -1;
	int delayOfGratification = -1;
	int innerVoice = -1;
	int selfRegulation = -1;
	int compositeFocus;
	int compositeImpulse;
}
