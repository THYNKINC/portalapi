package com.portal.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DaysSinceLastPlayedPerUser {
    private String user;
    private long daysSinceLastPlayed;
}
