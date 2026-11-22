package com.example.travelplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TravelplannerApplication {
    /** Spring Boot를 시작해 웹 화면과 REST API, 데이터베이스를 초기화합니다. */
    public static void main(String[] args) {
        SpringApplication.run(TravelplannerApplication.class, args);
    }
}
