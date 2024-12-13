package com.portal.api.dto.response;

import lombok.Data;

@Data
public class PlayerStatus {

    private GeneralStatus generalStatus;

    private String started;

    private String completed;

    private String dropped;
}

