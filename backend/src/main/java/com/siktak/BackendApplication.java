package com.siktak;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.siktak.common.config.ClockConfig;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ClockConfig.ZONE));
        SpringApplication.run(BackendApplication.class, args);
    }
}
