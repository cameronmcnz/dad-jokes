package com.mcnz.ai.jokes;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
@RestController
public class PickeringRagApplication {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final AtomicBoolean ingested = new AtomicBoolean(false);

    @Value("${rag.pdf.url}")
    private String pdfUrl;

    public PickeringRagApplication(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
    }

    public static void main(String[] args) {
        SpringApplication.run(PickeringRagApplication.class, args);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "ingested", ingested.get(),
            "pdfUrl", pdfUrl
        );
    }

    @GetMapping("/ask")
    public AskResponse ask(@RequestParam("question") String question) {
        if (!ingested.get()) {
            return new AskResponse("Not ready yet. PDF ingestion not completed.", List.of());
        }

        List<Document> topDocs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(5)
                .build()
        );

        StringBuilder context = new StringBuilder();
        List<SourceChunk> sources = new ArrayList<>();

        for (int i = 0; i < topDocs.size(); i++) {
            Document d = topDocs.get(i);
            String chunk = d.getText();

            context.append("CHUNK ").append(i + 1).append(":\n")
                   .append(chunk).append("\n\n");

            sources.add(new SourceChunk(
                i + 1,
                chunk.length() > 450 ? chunk.substring(0, 450) + "..." : chunk
            ));
        }

        String prompt = """
            You are answering questions using ONLY the provided context from a book.
            If the answer is not in the context, say: "I don't know based on the provided text."

            CONTEXT:
            %s

            QUESTION:
            %s
            """.formatted(context, question);

        String answer = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        return new AskResponse(answer, sources);
    }

    // -------- Ingestion helper methods --------

    private void ingestPdfIntoVectorStore(String url) throws Exception {
        String fullText = extractPdfText(url);

        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> docs = splitter.apply(List.of(new Document(fullText)));

        List<Document> enriched = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("source", "pickeringisspringfield.pdf");
            meta.put("chunk", i + 1);
            enriched.add(new Document(docs.get(i).getText(), meta));
        }

        vectorStore.add(enriched);
    }

    private String extractPdfText(String url) throws Exception {
        try (InputStream is = new URL(url).openStream();
             PDDocument doc = PDDocument.load(is)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    public record AskResponse(String answer, List<SourceChunk> sources) {}
    public record SourceChunk(int chunk, String preview) {}

    /**
     * Keeps ingestion simple without @Bean in this class (avoids circular refs).
     */
    @Component
    static class IngestRunner implements CommandLineRunner {
        private final PickeringRagApplication app;

        IngestRunner(PickeringRagApplication app) {
            this.app = app;
        }

        @Override
        public void run(String... args) throws Exception {
            //app.ingestPdfIntoVectorStore(app.pdfUrl);
            app.ingested.set(true);
        }
    }
}
