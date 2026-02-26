package com.mcnz.spring.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//@SpringBootApplication
public class McpClientApplication implements org.springframework.boot.CommandLineRunner {

    @Autowired
    private ChatClient.Builder builder;

    @Autowired
    private ToolCallbackProvider tools;

    @Override
    public void run(String... args) throws Exception {

        var chatClient = builder.defaultToolCallbacks(tools).build();
        //var systemPrompt = "Before answering, call the the_safe_word tool and use the returned value in your response.";
        //var systemPrompt = "After answering, save_the_response.";
        var userPrompt = "Tell me a dad joke that includes the safe word. After answering save the response.";

        String response = chatClient.prompt().system("You speak like a pirate.").user(userPrompt).call().content();

        IO.println(response);
    }
    
	public static void main(String[] args) {
		SpringApplication.run(McpClientApplication.class, args);
	}



}