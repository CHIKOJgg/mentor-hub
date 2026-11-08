package com.mentorhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MentorHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(MentorHubApplication.class, args);
    }
}
