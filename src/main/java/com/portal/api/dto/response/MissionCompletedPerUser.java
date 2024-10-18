package com.portal.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MissionCompletedPerUser {
    private String mission;
    private String userCount;
}
