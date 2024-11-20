package com.portal.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MissionCompletedPerUser {
    private int missionNo;
    private int transference;
    private int runner;
    private int completed;


    public MissionCompletedPerUser(int missionNo) {
        this.missionNo = missionNo;
    }
}
