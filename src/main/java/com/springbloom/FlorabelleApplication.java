package com.springbloom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point; component scanning starts at this package. */
@SpringBootApplication
public class FlorabelleApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlorabelleApplication.class, args);
    }
}
