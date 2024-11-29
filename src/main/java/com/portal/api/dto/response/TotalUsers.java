package com.portal.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TotalUsers {

    private int dropped;
    private int activelyPlaying;
    private int completed;
    private int notStarted;
}
