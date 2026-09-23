package dev.hamza.applytrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ApplytrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApplytrackApplication.class, args);
    }
}
