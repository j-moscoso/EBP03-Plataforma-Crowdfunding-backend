package com.ebp03.plataforma_crowdfunding_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PlataformaCrowdfundingBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlataformaCrowdfundingBackendApplication.class, args);
	}

}
