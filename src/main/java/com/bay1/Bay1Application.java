package com.bay1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.bay1.service.CardTransactionAuthorizationService;

@SpringBootApplication
public class Bay1Application implements CommandLineRunner {
	
	@Autowired CardTransactionAuthorizationService service;

	public static void main(String[] args) {
		SpringApplication.run(Bay1Application.class, args);
	}
	
	@Override
    public void run(String... args) {
		service.authTxn(args);
	}

}
