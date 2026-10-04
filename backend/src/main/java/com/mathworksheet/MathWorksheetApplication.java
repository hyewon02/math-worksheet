package com.mathworksheet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MathWorksheetApplication {

    public static void main(String[] args) {
        SpringApplication.run(MathWorksheetApplication.class, args);
    }
}
