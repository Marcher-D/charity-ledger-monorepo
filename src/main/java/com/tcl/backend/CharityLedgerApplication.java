package com.tcl.backend; // Phải trùng khớp với package cha của bạn

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CharityLedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CharityLedgerApplication.class, args);
    }
}