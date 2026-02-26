package com.mcnz.spring.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
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

    chatMemory.add(cid, new UserMessage(message));

    var history = chatMemory.get(cid);

    var answer = chatClient.prompt()
        .messages(history)
        .call()
        .content();

    chatMemory.add(cid, new AssistantMessage(answer));

    return answer;
  }
}