package com.quickprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // powers the expired-file cleanup job
public class QuickPrintApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuickPrintApplication.class, args);
	}

}
