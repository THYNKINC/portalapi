package com.portal.api.controllers;

import com.portal.api.dto.response.CohortDetailsResponse;
import com.portal.api.model.Child;
import com.portal.api.model.Cohort;
import com.portal.api.model.PortalUser;
import com.portal.api.services.CohortService;
import com.portal.api.util.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/portal/cohort/{cohortId}/details")
public class CohortDetailsController {

    private final CohortService cohortService;

    private final JwtService jwtService;

    public CohortDetailsController(CohortService cohortService, JwtService jwtService) {
        this.cohortService = cohortService;
        this.jwtService = jwtService;
    }

    @GetMapping()
    public ResponseEntity<CohortDetailsResponse> index(@PathVariable String cohortId, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, false, null);

        Cohort cohort = cohortService.getCohort(cohortId);
        if (cohort == null) {
            return ResponseEntity.notFound().build();
        }

        List<Child> children = cohortService.getChildrenFromCohort(cohort.getId());

        CohortDetailsResponse cohortDetailsResponse = cohortService.getCohortDetails(children);

        return ResponseEntity.ok(cohortDetailsResponse);
    }
}
