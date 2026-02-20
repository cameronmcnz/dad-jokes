package com.mcnz.ai.jokes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class SocialReactorApplication {

    private final ChatClient chatClient;

    private final List<String> sampleTweets = Arrays.asList(
        "Spring Boot saved my weekend. Shipping felt amazing.",
        "Nothing works today. Everything is broken. I hate debugging."
    );

    public SocialReactorApplication(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public static void main(String[] args) {
        SpringApplication.run(SocialReactorApplication.class, args);
    }

    @GetMapping("/react/sample")
    public List<ReactionResult> reactToSampleTweets() {
        List<ReactionResult> results = new ArrayList<>();
        for (String tweet : sampleTweets) {
            results.add(analyzeAndReact(tweet));
        }
        return results;
    }

    @GetMapping("/react")
    public ReactionResult react(@RequestParam("tweet") String tweet) {
        return analyzeAndReact(tweet);
    }

    private ReactionResult analyzeAndReact(String postText) {
        String safeText = (postText == null) ? "" : postText.trim();

        BeanOutputConverter<ReactionResult> converter =
            new BeanOutputConverter<>(ReactionResult.class);

        // IMPORTANT:
        // - Use <postText> and <format> (StringTemplate-style placeholders)
        // - Avoid literal JSON braces { } in the template (ST will parse them)
        String template = """
            You are a strict sentiment analyzer and persona-based responder.

            Analyze the following social media post:

            \"\"\"
            {postText}
            \"\"\"

            Steps:
            1) Classify sentiment as exactly one of: POSITIVE, NEGATIVE, NEUTRAL, MIXED.

            2) Choose persona based on sentiment:
               - POSITIVE -> motivational_speaker
               - NEUTRAL  -> wise_monk
               - NEGATIVE -> pirate
               - MIXED    -> wise_monk

            3) Write a short reply (under 40 words) in the selected persona tone.
               Workplace-safe. No profanity.

            Output requirements:
            - Return ONLY valid JSON.
            - Include EXACTLY these fields: input, sentiment, persona, response.
            - input must be the original post text (exactly).

            {format}
            """;

        PromptTemplate promptTemplate = new PromptTemplate(template);

        String prompt = promptTemplate.render(Map.of(
            "postText", safeText,
            "format", converter.getFormat()
        ));

        return chatClient
            .prompt()
            .user(prompt)
            .call()
            .entity(converter);
    }

    public record ReactionResult(
        String input,
        String sentiment,
        String persona,
        String response
    ) {}
}
