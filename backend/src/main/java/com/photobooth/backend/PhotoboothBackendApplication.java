package com.photobooth.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PhotoboothBackendApplication {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        // Allows Hibernate's bytecode library to run on very new JDKs
        System.setProperty("net.bytebuddy.experimental", "true");
        SpringApplication.run(PhotoboothBackendApplication.class, args);
    }
}
