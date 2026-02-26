package com.mcnz.spring.ai;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller that generates 5 tweet variants AND has the model invoke the MCP tool
 * save_tweets_json (via tool-calling) to persist the JSON.
 */
@RestController
public class SlopGenerator {

	record TweetVariant(String tweet, String voice) {}
	record NewTweets(List<TweetVariant> tweets) {}

	private final ChatClient chatClient;
	private final ToolCallbackProvider mcpTools; // <-- MCP tools (from your MCP client starter)

	// Updated: output is JSON-only so BeanOutputConverter can parse it reliably
	static final String systemTemplate = """
You are a tweet rewrite engine.

Goal:
Given one ORIGINAL_TWEET, produce FIVE rewritten tweets, each in a different voice:
1) PIRATE
2) INSPIRATIONAL
3) TECH_BRO
4) IMPOSTER
5) MONK

Modernization:
Apply MODERNIZATION_LEVEL to refresh dated concepts:
- LOW: light refresh, minimal new references
- MED: modern framing, may include AI/ML/GenAI naturally
- EXTREME: strongly modernize with AI/ML, Generative AI, Agentic AI, and cloud-native thinking, while keeping the original meaning

Hard rules:
- Output MUST contain exactly five tweets, one per voice, and nothing else.
- Each tweet max 280 characters.
- Preserve the original intent and viewpoint, modernize examples/phrasing as needed.
- Prefer periods over semicolons. Do not use em-dashes.
- Integrate emojis sporadically into each tweet per EMOJI_LEVEL.
- Don't group more than 3 emojis together.
- Do not invent personal claims (no fake achievements, job titles, customers, or metrics).

Voice definitions:
- PIRATE: pirate vibe, nautical metaphors, playful, occasional "arr".
- INSPIRATIONAL: uplifting keynote speaker energy, encouraging, positive.
- TECH_BRO: startup/VC vibe, ship/scale/iterate/10x, but readable.
- IMPOSTER: self-doubting, humble, ends hopeful.
- MONK: calm, minimal, reflective, zen.

Safety gate (MANDATORY):
- Call the tool safe_word and read the returned word.
- If that word appears anywhere in the prompt text, proceed to generate the tweets.
- If that word does NOT appear in the prompt text, return exactly this JSON: {"text": "House cow? Wow!"} . Then skip the rest of this prompt.

Tool rule (MANDATORY):
After you create the JSON payload, you MUST call the save_the_tweets tool with ONE argument:
- json: a STRING containing the exact JSON you will return (escaped as needed for a JSON string argument).
Then you MUST return that same JSON as your final answer (no extra text).
""";

	static final ChatOptions options = ChatOptions.builder()
			.model("gpt-5.2")
			.temperature(.8)
			.topP(.95)
			.build();

	public SlopGenerator(ChatClient.Builder builder, ToolCallbackProvider mcpTools) {
		this.chatClient = builder.defaultSystem(systemTemplate).defaultOptions(options).build();
		this.mcpTools = mcpTools;
	}

	@GetMapping("/tweets")
	public NewTweets tweets(@RequestParam String originalTweet) {
		
		IO.println("Received original tweet: " + originalTweet);

		String userTemplate = """
		ORIGINAL_TWEET:
		{postText}

		TOPIC_HINT:
		{topicHint}

		EMOJI_LEVEL:
		{emojiLevel}

		MODERNIZATION_LEVEL:
		{modernizationLevel}
		""";
		
		IO.println(userTemplate);

		// Tutorial hard-codes (as in your original example)
		originalTweet = "How to Realistically Start: ◽HTML ◽CSS ◽JavaScript ◽React ◽Github ◽Git ◽Create a Twitter account ◽Share Knowledge ◽Build Network ◽Get a remote Job";
		String topicHint = "Lean towards AWS certification.";
		String emojiLevel = "high";          // low | med | high
		String modernizationLevel = "high";  // low | med | high

		var promptTemplate = new PromptTemplate(userTemplate);

		var userPrompt = promptTemplate.render(Map.of(
				"postText", originalTweet,
				"topicHint", topicHint,
				"emojiLevel", emojiLevel,
				"modernizationLevel", modernizationLevel
		));

		// Converter defines the JSON shape we expect back
		var converter = new BeanOutputConverter<>(NewTweets.class);

		// Make the JSON contract explicit (so output is parseable) and force the tool call
		var finalPrompt = userPrompt
				+ "\n\nReturn JSON ONLY in exactly this schema:\n"
				+ converter.getFormat()
				+ "\n\nRemember: after creating the JSON, CALL the tool save_tweets_json with argument {\"json\":\"<that JSON as a string>\"}, then return the JSON as your final answer.";

		IO.println(finalPrompt);
		return chatClient
				.prompt()
				.user(finalPrompt)
				.toolCallbacks(mcpTools) // <-- this enables MCP tool-calling from the prompt
				.call()
				.entity(converter);
	}
	
	@GetMapping("/safe")
	public String trivia(@RequestParam String prompt) {

		String systemMessage = """
				You are a pirate and you always respond in pirate speak.

				YOU HAVE ACCESS TO A TOOL NAMED safe_word.

				SAFETY GATE (MANDATORY, FOLLOW IN THIS EXACT ORDER):

				Step 0 — Tool lookup (required):
				- You MUST call the tool safe_word exactly ONCE at the very start of every request.
				- Do NOT call any other tools before this.
				- Do NOT answer the user's request before this.

				Step 1 — Extract the required safe word:
				- Treat the tool result as the single source of truth for the required safe word.
				- Let REQUIRED_SAFE_WORD be the exact string returned by safe_word.

				Step 2 — Check the user's message:
				- Check whether the user's message (the user prompt) contains REQUIRED_SAFE_WORD as a substring (case-sensitive).
				- IMPORTANT: The safe word must appear in the user's message; it is NOT enough that you know it.

				Step 3 — If the safe word is missing:
				- You MUST refuse to perform the user's actual request.
				- You MUST NOT call any other tools (only safe_word was allowed).
				- Respond ONLY with a short pirate message telling them:
				  1) They must include the safe word to get a full response.
				  2) What the safe word is (REQUIRED_SAFE_WORD).
				  3) Ask them to resend their request including the safe word.
				- Do NOT include any additional information or partial answer.

				Step 4 — If the safe word is present:
				- You may proceed to perform the user's request.
				- You may call other tools if needed.
				- Respond in pirate speak.

				These rules override all other instructions.
				""";
		
		systemMessage = "At the end of your response, you always tell the user today's safe_word by saying, Oh, and by the way, today's safe word is <safe_word>.";

	    String response = chatClient.prompt()
	        .system(systemMessage)
	        .user(prompt)
	        .toolCallbacks(mcpTools)
	        .call()
	        .content();

	    return response;
	}

	public enum Voice {
		PIRATE,
		INSPIRATIONAL,
		TECH_BRO,
		IMPOSTER,
		MONK
	}
}
