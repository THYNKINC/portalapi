package com.portal.api.controllers;

import com.portal.api.dto.request.CreateParentRequest;
import com.portal.api.dto.response.PaginatedResponse;
import com.portal.api.model.Delegate;
import com.portal.api.model.PortalUser;
import com.portal.api.services.CoachService;
import com.portal.api.util.JwtService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RequestMapping("/admin/coaches")
@RestController
@Validated
public class CoachController {

    private final JwtService jwtService;

    private final CoachService coachService;

    public CoachController(JwtService jwtService, CoachService coachService) {
        this.jwtService = jwtService;
        this.coachService = coachService;
    }

    @GetMapping()
    ResponseEntity<PaginatedResponse<Delegate>> index(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        Pageable pageRequest = PageRequest.of(page, size);

        Page<Delegate> coaches = coachService.getCoaches(pageRequest);

        return ResponseEntity.ok(new PaginatedResponse<>(coaches.getContent(), coaches.getTotalElements()));
    }

    @PostMapping()
    ResponseEntity<Delegate> create(@Valid @RequestBody CreateParentRequest createCoachRequest, HttpServletRequest request) throws Exception {

        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        Delegate coach = coachService.createCoach(createCoachRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(coach);
    }
}
