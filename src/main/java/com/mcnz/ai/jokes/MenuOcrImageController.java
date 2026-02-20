package com.mcnz.ai.jokes;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/ocr/menu")
public class MenuOcrImageController {

    // --- Simple in-memory cache so downloads are easy without regenerating every time ---
    private static final ConcurrentHashMap<String, CachedResult> CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_SECONDS = 15 * 60; // 15 minutes

    private final ChatClient chatClient;
    private final ImageModel imageModel;

    // Update this path if you want; you can also use "menu.jpg" relative to working dir
    private final FileSystemResource menuImage = new FileSystemResource("C:\\_repos\\menu.jpg");

    public MenuOcrImageController(ChatClient.Builder builder, ImageModel imageModel) {
        this.chatClient = builder.build();
        this.imageModel = imageModel;
    }

    /**
     * 1) OCR + translate to French (text)
     * 2) Generate a clean French menu image (PNG bytes)
     * 3) Create an overlay image on the original menu (PNG bytes)
     * Returns JSON containing download links for both images.
     *
     * GET http://localhost:8080/ocr/menu/french
     */
    @GetMapping(value = "/french", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> frenchPackage() throws IOException {

        if (!menuImage.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "menu.jpg not found at: " + menuImage.getPath()));
        }

        cleanupCache();

        // Step A: OCR-ish extraction + translation (vision model)
        String frenchText = chatClient.prompt()
                .user(u -> u.text("""
                        You are an OCR engine.
                        Extract ALL visible text from this menu image.
                        Preserve line breaks as best as possible.
                        Keep prices next to items.
                        Translate the extracted text into French.
                        Return ONLY the French text.
                        """)
                        .media(MimeTypeUtils.IMAGE_JPEG, menuImage)
                )
                .call()
                .content();

        // Step B: Generate a clean, printable French menu image using ImageModel
        byte[] generatedFrenchMenuPng = generateFrenchMenuImageBytes(frenchText);

        // Step C: Create an overlay image on the original menu (no fancy bounding boxes)
        byte[] overlayFrenchMenuPng = overlayFrenchOnOriginal(menuImage.getFile(), frenchText);

        // Cache it for easy download
        String id = UUID.randomUUID().toString();
        CACHE.put(id, new CachedResult(frenchText, generatedFrenchMenuPng, overlayFrenchMenuPng, Instant.now()));

        // Download links
        return ResponseEntity.ok(Map.of(
                "id", id,
                "frenchText", frenchText,
                "downloadGeneratedPng", "/ocr/menu/french/download/" + id + "?mode=generated",
                "downloadOverlayPng", "/ocr/menu/french/download/" + id + "?mode=overlay"
        ));
    }

    /**
     * Downloads one of the generated images.
     *
     * mode=generated -> clean generated menu
     * mode=overlay   -> original menu with French text panel overlay
     *
     * GET http://localhost:8080/ocr/menu/french/download/{id}?mode=generated
     */
    @GetMapping(value = "/french/download/{id}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable String id,
                                           @RequestParam(defaultValue = "generated") String mode) {

        cleanupCache();

        CachedResult result = CACHE.get(id);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("No cached result for id=" + id + " (it may have expired).")
                            .getBytes(StandardCharsets.UTF_8));
        }

        byte[] bytes;
        String filename;

        if ("overlay".equalsIgnoreCase(mode)) {
            bytes = result.overlayPng;
            filename = "menu-french-overlay.png";
        } else {
            bytes = result.generatedPng;
            filename = "menu-french-generated.png";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setCacheControl(CacheControl.noStore());

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    // ----------------------------
    // Helpers
    // ----------------------------

    private byte[] generateFrenchMenuImageBytes(String frenchText) throws IOException {

        // 1) Generate a clean blank menu template (short prompt)
        String prompt = "Create a clean printable blank restaurant menu template. "
            + "Black on white, simple layout, generous margins. No food items, no text.";

        ImageResponse imageResponse = imageModel.call(new ImagePrompt(prompt));

        String b64 = imageResponse.getResult().getOutput().getB64Json();
        if (b64 == null || b64.isBlank()) {
            throw new IllegalStateException("Image model did not return base64 data. Ensure response_format=b64_json.");
        }

        byte[] templatePng = Base64.getDecoder().decode(b64);

        // 2) Overlay the full French text onto the template with Java2D
        BufferedImage template = ImageIO.read(new ByteArrayInputStream(templatePng));
        if (template == null) {
            throw new IllegalStateException("Could not decode generated template image.");
        }

        BufferedImage out = new BufferedImage(template.getWidth(), template.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(template, 0, 0, null);

        int margin = 60;
        int x = margin;
        int y = margin;

        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.drawString("Menu", x, y);
        y += 40;

        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        FontMetrics fm = g.getFontMetrics();
        int maxWidth = out.getWidth() - (margin * 2);
        int lineHeight = fm.getHeight() + 4;

        // Reuse your existing drawWrapped(...) helper
        for (String paragraph : frenchText.split("\\R\\R+")) {
            y = drawWrapped(g, paragraph.trim(), x, y, maxWidth, lineHeight);
            y += lineHeight;
            if (y > out.getHeight() - margin) break;
        }

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", baos);
        return baos.toByteArray();
    }

    /**
     * Simple overlay strategy:
     * - Keeps the original photo intact
     * - Adds a white panel on the right (or bottom if portrait is narrow)
     * - Renders the French text into the panel with basic word wrapping
     *
     * This avoids needing OCR bounding boxes.
     */
    private byte[] overlayFrenchOnOriginal(File originalJpg, String frenchText) throws IOException {

        BufferedImage src = ImageIO.read(originalJpg);
        if (src == null) {
            throw new IllegalArgumentException("Could not read image: " + originalJpg.getAbsolutePath());
        }

        int w = src.getWidth();
        int h = src.getHeight();

        // Create a new canvas with extra space for translation panel
        int panelWidth = Math.max(420, (int) (w * 0.45));
        boolean addRightPanel = (w >= 700); // if image is wide enough

        int outW = addRightPanel ? (w + panelWidth) : w;
        int outH = addRightPanel ? h : (h + Math.max(500, (int) (h * 0.35)));

        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();

        // Better text rendering
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw original
        g.drawImage(src, 0, 0, null);

        // Panel position
        int px = addRightPanel ? w : 0;
        int py = addRightPanel ? 0 : h;
        int pw = addRightPanel ? panelWidth : w;
        int ph = addRightPanel ? h : (outH - h);

        // Panel background (white)
        g.setColor(Color.WHITE);
        g.fillRect(px, py, pw, ph);

        // Panel border
        g.setColor(new Color(0, 0, 0, 40));
        g.drawRect(px, py, pw - 1, ph - 1);

        // Title
        int margin = 18;
        int x = px + margin;
        int y = py + margin;

        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString("Menu (Français)", x, y + 20);

        // Body text
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        FontMetrics fm = g.getFontMetrics();

        y += 48;

        int maxWidth = pw - (margin * 2);
        int lineHeight = fm.getHeight() + 2;

        for (String paragraph : frenchText.split("\\R\\R+")) {
            y = drawWrapped(g, paragraph.trim(), x, y, maxWidth, lineHeight);
            y += lineHeight; // blank line between paragraphs
            if (y > py + ph - margin) break; // avoid overflow
        }

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", baos);
        return baos.toByteArray();
    }

    private int drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        if (text.isBlank()) return y;

        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            String candidate = line.isEmpty() ? word : (line + " " + word);
            int width = fm.stringWidth(candidate);

            if (width <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
            } else {
                // draw current line
                g.drawString(line.toString(), x, y);
                y += lineHeight;
                // start new line with word
                line.setLength(0);
                line.append(word);
            }
        }

        if (!line.isEmpty()) {
            g.drawString(line.toString(), x, y);
            y += lineHeight;
        }
        return y;
    }

    private void cleanupCache() {
        Instant now = Instant.now();
        CACHE.entrySet().removeIf(e ->
                e.getValue().createdAt.plusSeconds(CACHE_TTL_SECONDS).isBefore(now)
        );
    }

    private static class CachedResult {
        final String frenchText;
        final byte[] generatedPng;
        final byte[] overlayPng;
        final Instant createdAt;

        CachedResult(String frenchText, byte[] generatedPng, byte[] overlayPng, Instant createdAt) {
            this.frenchText = frenchText;
            this.generatedPng = generatedPng;
            this.overlayPng = overlayPng;
            this.createdAt = createdAt;
        }
    }
}
