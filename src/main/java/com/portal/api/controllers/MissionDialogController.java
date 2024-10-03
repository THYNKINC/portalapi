package com.portal.api.controllers;

import com.portal.api.model.MissionDialog;
import com.portal.api.model.PortalUser;
import com.portal.api.services.MissionDialogService;
import com.portal.api.util.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/admin/mission-dialog")
@Validated
public class MissionDialogController {

    private final MissionDialogService missionService;
    private final JwtService jwtService;


    public MissionDialogController(MissionDialogService missionService, JwtService jwtService) {
        this.missionService = missionService;
        this.jwtService = jwtService;
    }

    @GetMapping
    public ResponseEntity<List<MissionDialog>> index(HttpServletRequest request) throws Exception {
        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        List<MissionDialog> missions = missionService.getAllMissions();
        return new ResponseEntity<>(missions, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MissionDialog> edit(@PathVariable String id, HttpServletRequest request) throws Exception {
        PortalUser user = jwtService.decodeJwtFromRequest(request, false, null);

        Optional<MissionDialog> mission = missionService.getMissionById(id);
        return mission.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<MissionDialog> update(@PathVariable String id, @Valid @RequestBody MissionDialog mission, HttpServletRequest request) {
        try {
            PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

            MissionDialog updatedMission = missionService.updateMission(id, mission);
            return new ResponseEntity<>(updatedMission, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
