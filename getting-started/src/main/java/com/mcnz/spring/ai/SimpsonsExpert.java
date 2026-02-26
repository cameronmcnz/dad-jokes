package com.mcnz.spring.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimpsonsExpert {
	
	ChatClient chatClient;
	
	public SimpsonsExpert(ChatClient.Builder builder) {
		chatClient =  builder.build();
	}

	@GetMapping("/trivia")
	public String trivia(@RequestParam String prompt) {

	    String systemMessage = """
	        You are a trivia assistant specialized ONLY in the TV show "The Simpsons".

	        Rules:
	        - Only answer questions related to The Simpsons.
	        - Only provide trivia about The Simpsons.
	        - If the question is not about The Simpsons, respond with:
	          "I can only answer trivia questions about The Simpsons."
	        - Do not answer non-Simpsons questions.
	        """;

	    String response = chatClient.prompt()
	        .system(systemMessage)
	        .user(prompt)
	        .call()
	        .content();

	    return response;
	}

}
