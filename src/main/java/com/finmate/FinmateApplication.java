package com.finmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FinmateApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinmateApplication.class, args);
	}

}
