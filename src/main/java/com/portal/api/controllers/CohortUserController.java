package com.portal.api.controllers;

import com.portal.api.dto.request.CreateCohortUserRequest;
import com.portal.api.model.Child;
import com.portal.api.model.PortalUser;
import com.portal.api.services.CohortService;
import com.portal.api.util.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/portal/cohorts/{cohortId}/users")
@Validated
public class CohortUserController {

    private final JwtService jwtService;

    private final CohortService cohortService;

    public CohortUserController(JwtService jwtService, CohortService cohortService) {
        this.jwtService = jwtService;
        this.cohortService = cohortService;
    }

    @PostMapping()
    ResponseEntity<Child> create(@Valid @RequestBody CreateCohortUserRequest createUserRequest, @PathVariable String cohortId, HttpServletRequest request) throws Exception {

        PortalUser coach = jwtService.decodeJwtFromRequest(request, false, null);

        Child child = cohortService.addUserToCohort(createUserRequest, cohortId, coach.getUsername(), jwtService.getAdminJwt());

        return ResponseEntity.status(HttpStatus.CREATED).body(child);
    }
}
