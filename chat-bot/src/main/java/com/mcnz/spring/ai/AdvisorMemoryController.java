package com.mcnz.spring.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/advisor")
class AdvisorMemoryController {

	ChatClient chatClient;

	AdvisorMemoryController(ChatClient.Builder builder, ChatMemory chatMemory) {

		var memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		this.chatClient = builder.defaultAdvisors(memoryAdvisor).build();
	}

	@GetMapping
	public String chat(@RequestParam String cid, @RequestParam String userMessage) {

		var prompt = chatClient.prompt().user(userMessage);
		var advisedPrompt = prompt.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, cid));
		return advisedPrompt.call().content();

	}

}

/*
 * return chatClient.prompt() .user(chatMessage) .advisors(spec ->
 * spec.param(ChatMemory.CONVERSATION_ID, cid) ) .call() .content();
 */