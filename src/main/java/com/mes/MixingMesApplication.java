package com.mes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MixingMesApplication {
    public static void main(String[] args) {
        SpringApplication.run(MixingMesApplication.class, args);
    }
}
