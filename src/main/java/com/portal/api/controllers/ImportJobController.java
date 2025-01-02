package com.portal.api.controllers;

import com.portal.api.model.ImportJob;
import com.portal.api.model.PortalUser;
import com.portal.api.services.ImportJobService;
import com.portal.api.util.JwtService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/admin/imports")
public class ImportJobController {

    private final JwtService jwtService;
    private final ImportJobService importJobService;

    public ImportJobController(JwtService jwtService, ImportJobService importJobService) {
        this.jwtService = jwtService;
        this.importJobService = importJobService;
    }

    @GetMapping()
    ResponseEntity<Page<ImportJob>> index(HttpServletRequest request,
                                                  @RequestParam(required = false) String partialName,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) throws Exception {
        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);

        Pageable pageRequest = PageRequest.of(page, size, Sort.Direction.DESC, "createdDate");

        return ResponseEntity.ok(importJobService.getImportJobs(pageRequest));
    }

    @GetMapping("/{jobId}")
    ResponseEntity<ImportJob> edit(HttpServletRequest request, @PathVariable String jobId) throws Exception {
        PortalUser user = jwtService.decodeJwtFromRequest(request, true, null);
        ImportJob importJob = importJobService.findById(jobId);

        return ResponseEntity.ok(importJob);
    }
}
