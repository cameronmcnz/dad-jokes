package com.mcnz.ai.jokes;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.StreamingTextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import reactor.core.publisher.Flux;

@RestController
public class MenuOcrController {

    private final ChatClient chatClient;
    private final TextToSpeechModel ttsModel;
    private final StreamingTextToSpeechModel streamingTtsModel;

    public MenuOcrController(ChatClient.Builder builder,
                             TextToSpeechModel ttsModel,
                             StreamingTextToSpeechModel streamingTtsModel) {
        this.chatClient = builder.build();
        this.ttsModel = ttsModel;
        this.streamingTtsModel = streamingTtsModel;
    }

    /**
     * Download a full MP3 of the menu being read out loud.
     * GET http://localhost:8080/ocr/menu.mp3
     */
    @GetMapping(value = "/ocr/menu.mp3", produces = "audio/mpeg")
    public ResponseEntity<byte[]> menuAsMp3() {

        var image = new FileSystemResource("C:\\_repos\\menu.jpg");
        if (!image.exists()) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body("menu.jpg not found.".getBytes(StandardCharsets.UTF_8));
        }

        // 1) OCR extract (you can also translate here if you want)
        String menuText = chatClient.prompt()
            .user(u -> u.text("""
                You are an OCR engine.
                Extract ALL visible text from this menu image.
                Preserve line breaks.
                Keep prices next to items.
                Return ONLY the extracted text.
                """)
                .media(MimeTypeUtils.IMAGE_JPEG, image))
            .call()
            .content();

        // 2) Make it sound good (optional “narration script” wrapper)
        String narration = """
            Here is the menu.
            Please note: Prices and availability may vary.

            %s
            """.formatted(menuText);

        // 3) Generate MP3 (chunk to avoid provider text limits)
        byte[] mp3 = synthesizeMp3Chunked(narration, 1800);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("audio/mpeg"));
        headers.setContentDisposition(
            ContentDisposition.attachment()
                .filename("menu-" + Instant.now().toEpochMilli() + ".mp3")
                .build()
        );

        return ResponseEntity.ok().headers(headers).body(mp3);
    }

    /**
     * Stream MP3 bytes as they are produced (MVC streaming).
     * GET http://localhost:8080/ocr/menu-stream.mp3
     *
     * Note: whether this truly streams depends on the provider/model.
     */
    @GetMapping(value = "/ocr/menu-stream.mp3", produces = "audio/mpeg")
    public ResponseEntity<StreamingResponseBody> menuAsMp3Stream() {

        var image = new FileSystemResource("C:\\_repos\\menu.jpg");
        if (!image.exists()) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body(out -> out.write("menu.jpg not found.".getBytes(StandardCharsets.UTF_8)));
        }

        String menuText = chatClient.prompt()
            .user(u -> u.text("""
                You are an OCR engine.
                Extract ALL visible text from this menu image.
                Preserve line breaks.
                Keep prices next to items.
                Return ONLY the extracted text.
                """)
                .media(MimeTypeUtils.IMAGE_JPEG, image))
            .call()
            .content();

        String narration = "Here is the menu.\n\n" + menuText;

        StreamingResponseBody body = (OutputStream os) -> {
            // Spring AI StreamingTextToSpeechModel can stream byte[] chunks. :contentReference[oaicite:2]{index=2}
            Flux<byte[]> flux = streamingTtsModel.stream(new TextToSpeechPrompt(narration))
                .map(resp -> resp.getResult().getOutput());

            flux.toStream().forEach(bytes -> {
                try {
                    os.write(bytes);
                    os.flush();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("audio/mpeg"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("menu-stream.mp3").build());
        return ResponseEntity.ok().headers(headers).body(body);
    }

    /**
     * Simple chunking: calls TTS multiple times and concatenates MP3 chunks.
     * MP3 concatenation generally works when the encoding settings match.
     */
    private byte[] synthesizeMp3Chunked(String text, int maxCharsPerChunk) {
        List<String> chunks = splitByParagraphs(text, maxCharsPerChunk);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (String chunk : chunks) {
            // TextToSpeechModel has a convenience call(String) that returns byte[]. :contentReference[oaicite:3]{index=3}
            byte[] audio = ttsModel.call(chunk);
            baos.writeBytes(audio);
        }
        return baos.toByteArray();
    }

    private List<String> splitByParagraphs(String text, int maxChars) {
        String[] paras = text.split("\\R\\R+");
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        for (String p : paras) {
            String block = p.trim();
            if (block.isEmpty()) continue;

            if (cur.length() + block.length() + 2 <= maxChars) {
                if (!cur.isEmpty()) cur.append("\n\n");
                cur.append(block);
            } else {
                if (!cur.isEmpty()) out.add(cur.toString());
                cur.setLength(0);

                // if a single paragraph is huge, hard-split it
                while (block.length() > maxChars) {
                    out.add(block.substring(0, maxChars));
                    block = block.substring(maxChars);
                }
                cur.append(block);
            }
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }
}
