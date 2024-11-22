package com.portal.api.dto.response;

import com.portal.api.model.Child;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CohortChildSummary extends Child {
    private String name;
    private LocalDate lastCompletedMission;
    private String nextMission;
    private Long daysSinceLastPlayed;
    private boolean active;

    public CohortChildSummary(Child child) {
        this.name = child.getFirstName() + " " + child.getLastName();
        this.setUsername(child.getUsername());
        this.setFirstName(child.getFirstName());
        this.setLastName(child.getLastName());
        this.setDob(child.getDob());
        this.setCreatedDate(child.getCreatedDate());
        this.setUpdatedDate(child.getUpdatedDate());
        this.setHeadsetId(child.getHeadsetId());
        this.setStartDate(child.getStartDate());
        this.setLabels(child.getLabels());
        this.setLocked(child.isLocked());
        this.setSchool(child.getSchool());
        this.setClassName(child.getClassName());
        this.setGender(child.getGender());
        this.setGrade(child.getGrade());
        this.setDiagnosis(child.getDiagnosis());
        this.setProvider(child.getProvider());
        this.setGroup(child.getGroup());
    }
}
