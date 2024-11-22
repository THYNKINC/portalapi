package com.portal.api.dto.response;

import com.portal.api.model.Child;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ChildWithCohortType {
    private Child child;
    private String cohortType;
}
