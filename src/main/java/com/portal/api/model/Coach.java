package com.portal.api.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@Data
@NoArgsConstructor
@Document(collection = "coach")
public class Coach extends PortalUser {
	
	@Builder.Default
	@Transient
	final Role role = Role.coach;

	private List<Cohort> cohorts;

	public void addCohort(Cohort cohort) {
		if (cohorts == null) {
			cohorts = new ArrayList<>();
		}
		cohorts.add(cohort);
	}
	public void removeCohort(Cohort cohort) {
		if (cohorts != null) {
			cohorts.remove(cohort);
		}
	}

	public void removeAllCohorts() {
		if (cohorts != null) {
			cohorts.clear();
		}
	}
}
