package com.example.sideworks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SideworksApplication {

    public static void main(String[] args) {
        SpringApplication.run(SideworksApplication.class, args);
    }

}
