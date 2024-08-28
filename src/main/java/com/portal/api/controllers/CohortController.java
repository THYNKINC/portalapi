package com.portal.api.controllers;

import com.portal.api.dto.request.CreateCohortRequest;
import com.portal.api.model.Cohort;
import com.portal.api.model.PortalUser;
import com.portal.api.services.CohortService;
import com.portal.api.util.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/portal/cohorts")
@Validated
public class CohortController {

    private final CohortService cohortService;

    private final JwtService jwtService;

    public CohortController(CohortService cohortService, JwtService jwtService) {
        this.cohortService = cohortService;
        this.jwtService = jwtService;
    }

    @GetMapping()
    ResponseEntity<List<Cohort>> index(HttpServletRequest request) throws Exception {

        PortalUser coach = jwtService.decodeJwtFromRequest(request, false, null);

        return ResponseEntity.ok(cohortService.getCohorts());
    }

    @GetMapping("{cohortId}")
    ResponseEntity<Cohort> edit(@PathVariable String cohortId, HttpServletRequest request) throws Exception {

        PortalUser coach = jwtService.decodeJwtFromRequest(request, false, null);

        return ResponseEntity.ok(cohortService.getCohort(cohortId));
    }

    @PostMapping()
    ResponseEntity<Cohort> create(@Valid @RequestBody CreateCohortRequest createCohortRequest, HttpServletRequest request) throws Exception {

        PortalUser coach = jwtService.decodeJwtFromRequest(request, false, null);

        return ResponseEntity.ok(cohortService.createCohort(createCohortRequest, coach.getUsername()));
    }

    @PutMapping("/{id}")
    ResponseEntity<Cohort> update(@Valid @RequestBody CreateCohortRequest updateCohortRequest, @PathVariable String id, HttpServletRequest request) throws Exception {

        PortalUser coach = jwtService.decodeJwtFromRequest(request, false, null);

        return ResponseEntity.ok(cohortService.update(updateCohortRequest, id, coach.getUsername()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest request) throws Exception {

        PortalUser coach = jwtService.decodeJwtFromRequest(request, false, null);

        cohortService.delete(id);

        return ResponseEntity.noContent().build();
    }
}
