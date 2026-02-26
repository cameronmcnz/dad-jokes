package com.mcnz.spring.ai;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class DiningMenusApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(DiningMenusApplication.class, args);
	}


    private final ChatClient chatClient;
    private final ImageModel imageModel;

    public DiningMenusApplication(ChatClient.Builder builder, ImageModel imageModel) {
        this.chatClient = builder.build();
        this.imageModel = imageModel;
    }

    // ======================
    // Models for JSON output
    // ======================

    public record Menu(
            String restaurantName,
            String sourceLanguage,
            String targetLanguage,
            String currencyHint,
            List<MenuItem> items
    ) {}

    public record MenuItem(
            String name,
            String description,
            Price price
    ) {}

    public record Price(
            String amount,
            String currency
    ) {}

    // ======================
    // Endpoints
    // ======================

    @GetMapping(value = "/menu/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> text() {
        String ocrText = convertToText();
        return ResponseEntity.ok(ocrText);
    }

    @GetMapping(value = "/menu/english", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> english() {
        String ocrText = convertToText();
        String englishHtml = convertToEnglishHtml(ocrText);
        return ResponseEntity.ok(englishHtml);
    }

    @GetMapping(value = "/menu/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Menu> json() {
        String ocrText = convertToText();
        String englishHtml = convertToEnglishHtml(ocrText);

        // Convert translated menu HTML into structured JSON
        Menu menu = convertEnglishHtmlToMenuJson(englishHtml);

        return ResponseEntity.ok(menu);
    }

	
    @GetMapping(value = "/menu/english/image", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> englishImage() {
        String ocrText = convertToText();
        String englishHtml = convertToEnglishHtml(ocrText);
        Menu menu = convertEnglishHtmlToMenuJson(englishHtml);

        String base64 = generateAccessibleMenuImageBase64(menu);

        String html = """
            <html>
              <body style="font-family: Arial, sans-serif;">
                <h2>Accessible Menu (High Contrast)</h2>
                <p>This is best-effort image rendering. For guaranteed readable text, render HTML/SVG to PNG server-side.</p>
                <img alt="High contrast menu"
                     style="max-width: 900px; width: 100%%; border: 1px solid #ccc;"
                     src="data:image/png;base64,%s"/>
              </body>
            </html>
            """.formatted(base64);

        return ResponseEntity.ok(html);
    }


    @GetMapping(value = "/menu/hero-image", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> heroImage() {
        String ocrText = convertToText();
        String englishHtml = convertToEnglishHtml(ocrText);
        Menu menu = convertEnglishHtmlToMenuJson(englishHtml);

        String base64 = generateHeroPosterBase64(menu);

        String html = """
            <html>
              <body style="font-family: Arial, sans-serif;">
                <h2>Hero Poster (Eye Candy)</h2>
                <p>Generated from restaurant + a few dish names (minimal text = better output).</p>
                <img alt="Restaurant hero poster"
                     style="max-width: 900px; width: 100%%; border: 1px solid #ccc;"
                     src="data:image/png;base64,%s"/>
              </body>
            </html>
            """.formatted(base64);

        return ResponseEntity.ok(html);
    }

    // ======================
    // Pipeline steps
    // ======================

    private String convertToText() {
        var image = new FileSystemResource("C:\\_repos\\menu.jpg");

        return chatClient.prompt()
                .system("""
                    You are an OCR engine.
                    Extract ALL readable text from the provided image.
                    Return ONLY the raw text.
                    Preserve line breaks where possible.
                    If something is unclear, include your best guess but do not add commentary.
                    """)
                .user(u -> u
                        .text("Run OCR on this menu image.")
                        .media(MimeTypeUtils.IMAGE_JPEG, image)
                )
                .call()
                .content();
    }

    private String convertToEnglishHtml(String ocrText) {
        return chatClient.prompt()
                .system("""
                    You are a professional menu translator.
                    Translate the user's text into English.
                    Keep the formatting (line breaks, headings, prices) as close as possible.
                    Do not add extra explanations.
                    Return the menu in HTML format for easy rendering.
                    """)
                .user(ocrText)
                .call()
                .content();
    }


    private Menu convertEnglishHtmlToMenuJson(String englishHtml) {
        return chatClient.prompt()
                .system("""
                    Convert the menu into JSON that matches the provided schema.
                    Rules:
                    - restaurantName: best guess from headings; if unknown use "Unknown Restaurant"
                    - sourceLanguage: best guess if you can infer; else "Unknown"
                    - targetLanguage: always "English"
                    - currencyHint: "USD", "EUR", or "Unknown" based on $, €, words like euros/dollars
                    - items: include only actual menu items (not section headings)
                    - price.amount: numeric as a string, e.g. "12.50" or "12"
                    - price.currency: "USD", "EUR", or "Unknown"
                    - description: empty string if missing
                    Return ONLY valid JSON (no markdown, no commentary).
                    """)
                .user("""
                    Here is the translated menu HTML. Convert it into structured JSON:

                    %s
                    """.formatted(englishHtml))
                .call()
                .entity(Menu.class);
    }

    // ======================
    // Image generation
    // ======================

    private String generateAccessibleMenuImageBase64(Menu menu) {
        String prompt = buildAccessibleMenuImagePrompt(menu);

        // Spring AI 2.0.0-M2: no size(String). Use width/height.
        var options = OpenAiImageOptions.builder()
                //.model("dall-e-3")
        		.model("chatgpt-image-latest")
                .width(1)
                .height(1024)
                .responseFormat("b64_json")
                .N(1)
                .build();

        ImageResponse response = imageModel.call(new ImagePrompt(prompt, options));

        String b64 = response.getResult().getOutput().getB64Json();
        if (b64 == null || b64.isBlank()) {
            String url = response.getResult().getOutput().getUrl();
            throw new IllegalStateException("Expected base64 image but got URL (check responseFormat). url=" + url);
        }
        return b64;
    }

    private static String buildAccessibleMenuImagePrompt(Menu menu) {
        String restaurant = Objects.requireNonNullElse(menu.restaurantName(), "Unknown Restaurant").trim();
        String currencyHint = Objects.requireNonNullElse(menu.currencyHint(), "Unknown").trim();

        // Keep output smaller to help legibility. If you want more, paginate.
        List<MenuItem> items = menu.items() == null ? List.of() : menu.items().stream().limit(12).toList();

        var sb = new StringBuilder("""
            Create a flat, print-ready restaurant menu image with EXTREMELY readable text.

            Non-negotiable requirements:
            - White background (#FFFFFF).
            - Pure black text (#000000).
            - Clean sans-serif font (Helvetica/Arial style).
            - VERY LARGE font and generous spacing.
            - One column layout.
            - No photos. No icons. No textures. No gradients. No shadows. No perspective.
            - Do not invent items. Use only what is provided.
            - Avoid tiny text at all costs.

            Content (English):
            """);

        sb.append("\nTITLE: ").append(restaurant).append("\n");
        sb.append("CURRENCY HINT: ").append(currencyHint).append("\n\n");

        int i = 1;
        for (var item : items) {
            String name = Objects.requireNonNullElse(item.name(), "").trim();
            String desc = Objects.requireNonNullElse(item.description(), "").trim();

            String amount = "";
            String currency = "Unknown";
            if (item.price() != null) {
                amount = Objects.requireNonNullElse(item.price().amount(), "").trim();
                currency = Objects.requireNonNullElse(item.price().currency(), "Unknown").trim();
            }

            if (name.isBlank()) continue;

            sb.append(i++).append(") ").append(name);
            if (!amount.isBlank()) {
                sb.append(" .... ").append(amount);
                if (!currency.equalsIgnoreCase("Unknown")) sb.append(" ").append(currency);
            }
            sb.append("\n");

            // keep descriptions short
            if (!desc.isBlank()) {
                sb.append("   ").append(desc.length() > 80 ? desc.substring(0, 80) + "…" : desc).append("\n");
            }
            sb.append("\n");
        }

        sb.append("""
            Layout rules:
            - Title at top, largest text.
            - Each item: name on one line, optional short description below, price aligned to the right using dots.
            - Big margins and spacing between items.
            """);

        return sb.toString();
    }

    private String generateHeroPosterBase64(Menu menu) {
        String prompt = buildHeroPosterPrompt(menu);

        var options = OpenAiImageOptions.builder()
                .model("dall-e-3")
                .width(1024)
                .height(1792)
                .responseFormat("b64_json")
                .N(1)
                .build();

        ImageResponse response = imageModel.call(new ImagePrompt(prompt, options));

        String b64 = response.getResult().getOutput().getB64Json();
        if (b64 == null || b64.isBlank()) {
            String url = response.getResult().getOutput().getUrl();
            throw new IllegalStateException("Expected base64 image but got URL (check responseFormat). url=" + url);
        }
        return b64;
    }

    private static String buildHeroPosterPrompt(Menu menu) {
        String restaurant = (menu.restaurantName() == null || menu.restaurantName().isBlank())
                ? "Unknown Restaurant"
                : menu.restaurantName().trim();

        List<String> dishNames = (menu.items() == null) ? List.of()
                : menu.items().stream()
                    .map(MenuItem::name)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .limit(6)
                    .toList();

        String subtitle = dishNames.isEmpty()
                ? "Chef’s Specials"
                : String.join(" • ", dishNames);

        return """
            Create a beautiful hero image image (1024x1792) inspired by the restaurant and dishes below.

            Critical requirements:
            - Make it visually striking and professional.
            - Keep text MINIMAL and LARGE so it stays readable.
            - Use only 2 lines of text:
              Line 1 (big title): "%s"
              Line 2 (smaller subtitle): "%s"
            - Do NOT add any other words or labels.
            - Do NOT include prices.
            - No watermark, no extra logos, no signature.

            Style guidance (choose ONE, not both):
            - Option A: cinematic food photography aesthetic, warm lighting, shallow depth of field.
            - Option B: elegant minimal illustration, premium bistro vibe.

            Composition:
            - strong focal point (plate / ingredients / table setting)
            - high contrast
            - uncluttered
            """.formatted(menu.restaurantName, menu.items().toString().substring(0, 3000));
    }
    
}