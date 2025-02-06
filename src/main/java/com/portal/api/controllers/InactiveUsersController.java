package com.portal.api.controllers;

import com.portal.api.model.Child;
import com.portal.api.services.AnalyticsService;
import com.portal.api.services.CoachService;
import com.portal.api.services.ParentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/admin")

public class InactiveUsersController {

    private final AnalyticsService analyticService;

    private final ParentService parentService;

    private final CoachService coachService;

    public InactiveUsersController(AnalyticsService analyticService, ParentService parentService, CoachService coachService) {
        this.analyticService = analyticService;
        this.parentService = parentService;
        this.coachService = coachService;
    }

    @GetMapping("/inactive-users")
    public ResponseEntity<String> getAllInactiveUsers() throws Exception {
        List<String> usernames = analyticService.getUniqueUsernamesForSessions();

        List<Child> parentsChildren = parentService.getAllChildren();
        List<Child> coachChildren = coachService.getAllChildren();

        List<Child> inactiveChildren = Stream.concat(parentsChildren.stream(), coachChildren.stream())
                .filter(child -> !usernames.contains(child.getUsername()))
                .toList();

        StringBuilder csvOutput = new StringBuilder();
        csvOutput.append("Username, First Name, Last Name\n");

        for (Child child : inactiveChildren) {
            csvOutput.append(child.getUsername())
                    .append(", ")
                    .append(child.getFirstName())
                    .append(", ")
                    .append(child.getLastName())
                    .append("\n");
        }

        return ResponseEntity.ok()
                .body(csvOutput.toString());
    }

    @GetMapping("/active-users")
    public ResponseEntity<String> getAllActiveUsers() throws Exception {
        List<String> usernames = analyticService.getUniqueUsernamesForSessions();

        List<Child> parentsChildren = parentService.getAllChildren();
        List<Child> coachChildren = coachService.getAllChildren();

        List<Child> inactiveChildren = Stream.concat(parentsChildren.stream(), coachChildren.stream())
                .filter(child -> usernames.contains(child.getUsername()))
                .toList();

        StringBuilder csvOutput = new StringBuilder();
        csvOutput.append("Username, First Name, Last Name\n");

        for (Child child : inactiveChildren) {
            csvOutput.append(child.getUsername())
                    .append(", ")
                    .append(child.getFirstName())
                    .append(", ")
                    .append(child.getLastName())
                    .append("\n");
        }

        return ResponseEntity.ok()
                .body(csvOutput.toString());
    }
}