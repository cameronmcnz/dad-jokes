package com.mcnz.ai.jokes;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class DadjokesApplication {

	@Autowired
	ChatClient.Builder builder;

    @GetMapping("/joke")
    public String joke(
            @RequestParam(defaultValue = "software development") String topic) {

        String prompt = "Tell me a short, clean dad joke about " + topic;

        var chatClient = builder.build();
        
        return chatClient
                .prompt(prompt)
                .call()
                .content();
    }
    
    public static void main(String[] args) {
        SpringApplication.run(DadjokesApplication.class, args);
    }
}
