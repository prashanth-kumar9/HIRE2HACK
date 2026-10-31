package com.company.screening;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InterviewBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewBookingApplication.class, args);
    }
}
