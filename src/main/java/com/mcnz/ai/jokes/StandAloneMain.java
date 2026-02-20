package com.mcnz.ai.jokes;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class StandAloneMain implements CommandLineRunner {
	
    @Autowired
    private ChatClient.Builder builder;

    @Override
    public void run(String... args) {

        var chatClient = builder.build();

        String response = chatClient.prompt("Tell me a Dad joke.").call().content();

        IO.println(response);
    }
    
    void main(String[] args) {
        SpringApplication.run(DadjokesApplication.class, args);
    }

}
