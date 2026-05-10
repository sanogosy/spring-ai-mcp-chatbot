package org.alfresco.ai_framework.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for handling chat interactions with the AI system.
 * Configures chat advisors, such as QuestionAnswerAdvisor and SafeGuardAdvisor,
 * to enrich responses and ensure safe interactions.
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    /**
     * Constructs the ChatService with a pre-configured ChatClient and VectorStore.
     *
     * @param chatClientBuilder Builder for creating a ChatClient instance.
     * @param vectorStore       Vector store for performing document searches.
     */
    public ChatService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        logger.debug("ChatService initialized with ChatClient and VectorStore.");
    }

    /**
     * Processes a chat query by interacting with the AI through configured advisors.
     * Uses a QuestionAnswerAdvisor for document retrieval.
     *
     * @param query The user input to process.
     * @return The AI-generated ChatResponse, containing the answer and metadata.
     */
//    public ChatResponse chat(String query) {
//        logger.info("Processing chat query: {}", query);
//
//        // Configuring advisors to enhance the response quality
//        ChatResponse response = chatClient
//                .prompt()
//                .advisors(new QuestionAnswerAdvisor(vectorStore))
//                .user(query)
//                .call()
//                .chatResponse();
//
//        logger.info("Received response from AI");
//        return response;
//    }

    public ChatResponse chat(String query) {
        logger.info("Processing chat query manually: {}", query);

        List<Document> relevantDocs = vectorStore.similaritySearch(query);
        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        logger.info("Processing chat query manually context: {}", context);

        ChatResponse response = chatClient.prompt()
                .system("""
            You are a helpful assistant. Answer the user's question only using the provided context.
            If the answer cannot be found in the context, say that you don't know.
            
            Context:
            """ + context) // Pass the context as part of the system message
                .user(query) // Pass the user question
                .call()
                .chatResponse();
        logger.info("Received manual response from AI");
        return response;
    }

}
