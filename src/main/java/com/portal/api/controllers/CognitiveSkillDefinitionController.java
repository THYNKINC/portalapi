package com.portal.api.controllers;

import com.portal.api.exception.ResourceNotFoundException;
import com.portal.api.model.CognitiveSkillDefinitionContainer;
import com.portal.api.model.PortalUser;
import com.portal.api.services.CognitiveSkillDefinitionService;
import com.portal.api.util.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/admin/cognitive-skill-definition")
@Validated
public class CognitiveSkillDefinitionController {

    private final CognitiveSkillDefinitionService service;
    private final JwtService jwtService;


    public CognitiveSkillDefinitionController(CognitiveSkillDefinitionService service, JwtService jwtService) {
        this.service = service;
        this.jwtService = jwtService;
    }

    @GetMapping
    public ResponseEntity<CognitiveSkillDefinitionContainer> index(HttpServletRequest request) throws Exception {
        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        List<CognitiveSkillDefinitionContainer> containers = service.getAll();

        if (containers.isEmpty()) {
            return new ResponseEntity<>(new CognitiveSkillDefinitionContainer(), HttpStatus.OK);
        }

        return new ResponseEntity<>(containers.get(0), HttpStatus.OK);
    }

    @PostMapping()
    public ResponseEntity<CognitiveSkillDefinitionContainer> update(@Valid @RequestBody CognitiveSkillDefinitionContainer container, HttpServletRequest request) throws Exception {
        try {
            PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

            CognitiveSkillDefinitionContainer updatedContainer = service.createOrUpdate(container);
            return new ResponseEntity<>(updatedContainer, HttpStatus.OK);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
