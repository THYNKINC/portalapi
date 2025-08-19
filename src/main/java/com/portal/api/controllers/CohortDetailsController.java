package com.portal.api.controllers;

import com.portal.api.dto.response.CohortDetailsResponse;
import com.portal.api.model.PortalUser;
import com.portal.api.services.CohortDetailsService;
import com.portal.api.util.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/portal/cohort/{cohortId}/details")
public class CohortDetailsController {

    private final CohortDetailsService cohortDetailsService;


    private final JwtService jwtService;

    public CohortDetailsController(CohortDetailsService cohortDetailsService, JwtService jwtService) {
        this.cohortDetailsService = cohortDetailsService;
        this.jwtService = jwtService;
    }

    @GetMapping()
    public ResponseEntity<CohortDetailsResponse> index(@PathVariable String cohortId, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, false, null);

        try {
            return ResponseEntity.ok(cohortDetailsService.getCohortDetails(cohortId));
        } catch (NoSuchElementException nse) {
            return ResponseEntity.notFound().build();
        }
    }
}
