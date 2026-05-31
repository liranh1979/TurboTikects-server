package com.turbotikects.turbotikectsserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TurboTikectsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TurboTikectsServerApplication.class, args);
    }

}
