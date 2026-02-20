package com.mcnz.ai.jokes;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manual")
public class ManualMemoryController {

  private final ChatClient chatClient;
  private final ChatMemory chatMemory;

  ManualMemoryController(ChatClient.Builder builder, ChatMemory chatMemory) {
    this.chatClient = builder.build();
    this.chatMemory = chatMemory;
  }

  @GetMapping
  public String chat(@RequestParam String cid, @RequestParam String message) {

    // 1) Store user message
    chatMemory.add(cid, new UserMessage(message));

    // 2) Pull full history for this conversation
    List<Message> history = chatMemory.get(cid);

    // 3) Call the model with the full history
    String answer = chatClient.prompt()
        .messages(history)
        .call()
        .content();

    // 4) Store assistant response
    chatMemory.add(cid, new AssistantMessage(answer));

    return answer;
  }
}


@RestController
@RequestMapping("/advisor")
class AdvisorMemoryController {

  private final ChatClient chatClient;

  AdvisorMemoryController(ChatClient advisorChatClient) {
    this.chatClient = advisorChatClient;
  }

  @GetMapping
  public String chat(@RequestParam String cid, @RequestParam String message) {
    return chatClient.prompt()
        .user(message)
        // Spring AI 2.x standard: pass conversation id via ChatMemory.CONVERSATION_ID
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
        .call()
        .content();
  }
}

@Configuration
class ChatClientWithMemoryAdvisorConfig {

  @Bean
  ChatClient advisorChatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
    return builder
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();
  }
}


