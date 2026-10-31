package com.example.hackathon.hackathon.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Hello", description = "Hello API endpoints")
public class HelloController {

    @GetMapping("/hello")
    @Operation(summary = "Say hello", description = "Returns a hello message")
    @ApiResponse(responseCode = "200", description = "Hello message returned successfully")
    public String hello() {
        return "Hello from API v1!";
    }
}
