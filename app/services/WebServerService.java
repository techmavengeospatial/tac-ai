package com.example.atak.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.example.atak.services")
public class WebServerService {

    public static void main(String[] args) {
        SpringApplication.run(WebServerService.class, args);
    }
}
