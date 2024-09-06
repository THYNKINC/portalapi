package com.portal.api.controllers;

import com.portal.api.model.Cohort;
import com.portal.api.model.PortalUser;
import com.portal.api.services.CohortService;
import com.portal.api.util.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/admin/coaches/{coachId}/cohorts")
@Validated
public class CoachCohortController {

    private final JwtService jwtService;

    private final CohortService cohortService;

    public CoachCohortController(JwtService jwtService, CohortService cohortService) {
        this.jwtService = jwtService;
        this.cohortService = cohortService;
    }

    @GetMapping()
    ResponseEntity<List<Cohort>> index(@PathVariable String coachId, HttpServletRequest request) throws Exception {

        PortalUser coach = jwtService.decodeJwtFromRequest(request, true, null);

        return ResponseEntity.ok(cohortService.getCohorts(coachId));
    }
}
