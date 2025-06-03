package com.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello from Kubernetes! This is a demo application.";
    }

    @GetMapping("/health")
    public String health() {
        return "Application is healthy!";
    }
} 