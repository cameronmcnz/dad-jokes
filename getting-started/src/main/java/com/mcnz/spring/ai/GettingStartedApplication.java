package com.mcnz.spring.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GettingStartedApplication implements CommandLineRunner {
	
    @Autowired
    ChatClient.Builder builder;
	
	
	@Override
	public void run(String... args) throws Exception {
		
		var chatClient = builder.build();
		String response = chatClient.prompt("Tell me a Dad joke.").call().content();
		IO.println(response);
		
	}
	
	
	public static void main(String[] args) {
		SpringApplication.run(GettingStartedApplication.class, args);
	}

}