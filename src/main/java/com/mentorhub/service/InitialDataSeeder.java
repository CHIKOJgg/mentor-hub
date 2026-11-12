package com.mentorhub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * InitialDataSeeder: demo data seeding permanently disabled.
 * Production maintains strictly authentic user records.
 */
@Slf4j
@Component
public class InitialDataSeeder {

    public void seedInitialDataIfEmpty() {
        log.debug("InitialDataSeeder: Mock data seeding is disabled.");
    }
}
