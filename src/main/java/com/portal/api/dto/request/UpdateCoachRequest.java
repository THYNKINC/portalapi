package com.portal.api.dto.request;

import lombok.Data;

@Data
public class UpdateCoachRequest {
    private String firstName;
    private String lastName;
    private String salutation;
}
