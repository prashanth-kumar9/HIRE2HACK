package com.example.hackathon.hackathon.controller;

import com.example.hackathon.hackathon.entity.Application;
import com.example.hackathon.hackathon.repository.ApplicationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Tag(name = "Application Management", description = "APIs for managing candidate applications and interview selection")
public class ApplicationController {

    private final ApplicationRepository applicationRepository;

    @PostMapping
    @Operation(summary = "Create a new application", description = "Creates a new candidate application record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Application created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public ResponseEntity<Application> createApplication(
            @Parameter(description = "Application details to create") @RequestBody Application application) {
        Application saved = applicationRepository.save(application);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    @Operation(summary = "Get all applications", description = "Retrieves all candidate applications")
    @ApiResponse(responseCode = "200", description = "Applications retrieved successfully")
    public ResponseEntity<List<Application>> getAllApplications() {
        List<Application> applications = applicationRepository.findAll();
        return ResponseEntity.ok(applications);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application by ID", description = "Retrieves a specific application by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Application found"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<Application> getApplicationById(
            @Parameter(description = "Application ID") @PathVariable Long id) {
        return applicationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/select")
    @Operation(summary = "Select candidate for interview", description = "Marks a candidate as selected for interview and triggers notification process")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Candidate selected successfully"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<Application> selectCandidate(
            @Parameter(description = "Application ID") @PathVariable Long id) {
        return applicationRepository.findById(id)
                .map(application -> {
                    application.setSelected(true);
                    application.setSelectedAt(LocalDateTime.now());
                    application.setHrScheduled(LocalDateTime.now());
                    Application updated = applicationRepository.save(application);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete application", description = "Deletes an application by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Application deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<Void> deleteApplication(
            @Parameter(description = "Application ID") @PathVariable Long id) {
        if (applicationRepository.existsById(id)) {
            applicationRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
