package com.mcnz.spring.ai;

import java.net.URI;

// RagController.java
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;

@RestController
public class RagController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    
    public RagController(ChatClient.Builder builder, EmbeddingModel embeddingModel, ChatMemory chatMemory) {
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.chatClient = builder.defaultAdvisors(memoryAdvisor).build();
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build(); 
    }
    
    @PostConstruct
    void ingestBookOnStartup() throws Exception {

        var resolver = new PathMatchingResourcePatternResolver();
        var pdfResources = resolver.getResources("classpath:pdfs/*.pdf");

        for (Resource pdfResource : pdfResources) {
            var pages = new PagePdfDocumentReader(pdfResource).get(); // returns List<Document
            vectorStore.add(pages);
            IO.println("✅ RAG ingestion complete: " + pdfResource.getFilename());
        }
    }

    @GetMapping("/ask")
    public String ask(@RequestParam(required = false) String cid,
                      @RequestParam boolean pirate,
                      @RequestParam String question) {
    	
    	
    	System.out.println(pirate);
    	
    	var resolvedCid = (cid == null || cid.isBlank()) ? "default" : cid;
    	final String finalCid = resolvedCid;
    	
    	var retrievalQuery = SearchRequest.builder().query(question).topK(7).build();
        var retrievedPages = vectorStore.similaritySearch(retrievalQuery);

        var augmentedContext = new StringBuilder();
        for (int i = 0; i < retrievedPages.size(); i++) {
            var page = retrievedPages.get(i);
            augmentedContext.append("\n\n Page from the Book: ").append(page.getText());
        }
        
        IO.println(augmentedContext);
        
        var piratePrompt = """
                You are a sarcastic pirate who is also an expert proposal writer who is helping to put together text, paragraphs and documents to assist
                Toronto Ontario's Pheonix Arise Foundation in obtaining funding for their various missions.
                
                Your responses are over the top in their advocacy for Phoenix Arise in a way that would encourage officials to fund the organization.
                
                You do not use em-dashes and you do not overuse semicolons. You prefer periods to separate sentences. 
                
                You do not invent personal claims, such as fake achievements, job titles, customers or metrics.
                
                You write in a voice that is heavily pirate and you use lots of emojis, especially sea and funeral related ones.
                
                You are answering questions primarily using the context provided from  documents used to describe what Pheonix Arise Foundation does.
                As much as possible, use the CONTEXT information provided to create a response.
                
                Render the response with HTML markup, as the resposne will be embedded within a <DIV> tag in an HTML page.
                
                %s

                CONTEXT:
                %s

                QUESTION:
                %s
                """.formatted(pirate ? "If pirate mode is enabled, instead of being professional, respond in a funny pirate voice with plenty of pirate related emojis." : "",
                              augmentedContext, question);

        var nonPiratePrompt = """
            You are an expert proposal writer who is helping to put together text, paragraphs and documents to assist
            Toronto Ontario's Pheonix Arise Foundation in obtaining funding for their various missions.
            
            Your responses may subtly, but always professonially, advocate for Phoenix Arise in a way that would encourage officials to fund the organization.
            
            You do not use em-dashes and you do not overuse semicolons. You prefer periods to separate sentences. 
            
            You do not invent personal claims, such as fake achievements, job titles, customers or metrics.
            
            You write in a voice that is professional and suitable for government document submissions, grant applications and funding proposals.
            
            You are answering questions primarily using the context provided from  documents used to describe what Pheonix Arise Foundation does.
            As much as possible, use the CONTEXT information provided to create a response.
            
            Render the response with HTML markup, as the resposne will be embedded within a <DIV> tag in an HTML page.
            
            %s

            CONTEXT:
            %s

            QUESTION:
            %s
            """.formatted(pirate ? "If pirate mode is enabled, instead of being professional, respond in a funny pirate voice with plenty of pirate related emojis." : "",
                          augmentedContext, question);
        
        var prompt = nonPiratePrompt;
        if (pirate) { 
			prompt = piratePrompt;
		} 
        
        var userPrompt = chatClient.prompt().user(prompt);
        var advisedPrompt = userPrompt.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, finalCid));
        return advisedPrompt.call().content();
    }
}






















