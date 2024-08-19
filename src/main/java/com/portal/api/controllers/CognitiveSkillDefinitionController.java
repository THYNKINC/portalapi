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
import java.util.Optional;

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
    public ResponseEntity<List<CognitiveSkillDefinitionContainer>> index(HttpServletRequest request) throws Exception {
        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        List<CognitiveSkillDefinitionContainer> containers = service.getAll();
        return new ResponseEntity<>(containers, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CognitiveSkillDefinitionContainer> edit(@PathVariable String id, HttpServletRequest request) throws Exception {
        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        Optional<CognitiveSkillDefinitionContainer> container = service.getById(id);
        return container.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CognitiveSkillDefinitionContainer> update(@PathVariable String id, @Valid @RequestBody CognitiveSkillDefinitionContainer container, HttpServletRequest request) throws Exception {
        try {
            PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

            CognitiveSkillDefinitionContainer updatedContainer = service.update(id, container);
            return new ResponseEntity<>(updatedContainer, HttpStatus.OK);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
