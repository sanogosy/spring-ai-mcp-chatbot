package com.mcpclient.clientmcp.agents;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MyAiAgent {

    private final ChatClient chatClient;
    private final List<McpAsyncClient> mcpAsyncClients;
    private final String DEFAULT_SYSTEM_PROMPT = """
            You are an AI agent connected to an MCP server.
            
            Always respond in the user's language.
            
            --------------------------------
            STRICT RESPONSE RULES
            --------------------------------
            
            You must NEVER invent information.
            
            You must ONLY use the content returned by tools.
            
            You must NEVER invent tool names.
            
            You may ONLY call the following tools:
            
            - get-alfresco-informations
            - search-hybrid-postgreSQL
            - search-hybrid
            
            If a tool name is not in this list, DO NOT use it.
            
            --------------------------------
            TOOL CALL FORMAT
            --------------------------------
            
            A tool call MUST follow EXACTLY this structure:
            
            {
              "name": "<tool-name>",
              "parameters": {
                "query": "<EXACT user question>"
              }
            }
            
            Rules:
            - query MUST be exactly the user question
            - NEVER translate the query
            - NEVER modify the query
            - NEVER shorten the query
            - query must NEVER be null
            
            --------------------------------
            PRIMARY TOOL SELECTION
            --------------------------------
            
            If the question contains any of these words:
            alfresco, mcp, rag, architecture
            → get-alfresco-informations
            
            If the question contains any of these words:
            article, categorie, category, catégorie, voiture
            → search-hybrid-postgreSQL
       
            
            --------------------------------
            MERGING RULE
            --------------------------------
            
            After both tool responses:
            
            - merge documents
            - remove duplicates
            - ignore entries containing only:
              IMAGE :
              Description of the image
            
            --------------------------------
            SUMMARY RULES
            --------------------------------
            
            When writing the summary:
            
            - use ONLY retrieved document text
            - maximum 4 sentences
            - do NOT invent explanations
            - do NOT use external knowledge
            
            If the retrieved information is insufficient:
            
            Return:
            
            {
            "documents": [],
            "images": [],
            "message": "Je n'ai pas trouvé suffisamment d'informations. Pouvez-vous préciser votre demande ?"
            }
            
            --------------------------------
            IMAGE URL RULE
            --------------------------------
            
            Convert:
            
            /alfrescoimages/<filename>
            
            to
            
            http://localhost:8083/api/v1/chat/<filename>
            
            --------------------------------
            FINAL RESPONSE FORMAT
            --------------------------------
            
            Return ONLY:
            
            {
            "documents": [],
            "images": [],
            "message": ""
            }
        """;

    private final String DEFAULT_SYSTEM_PROMPT_2 = """
            You are an AI agent connected to an MCP server.
            
            Always respond in the user's language.
            
            --------------------------------
            STRICT RESPONSE RULES
            --------------------------------
            
            You must NEVER invent information.
            
            You must ONLY use the content returned by tools.
            
            You must NEVER invent tool names.
            
            You may ONLY call the following tools:
            
            - search-hybrid-postgreSQL
            
            If a tool name is not in this list, DO NOT use it.
            
            --------------------------------
            TOOL CALL FORMAT
            --------------------------------
            
            A tool call MUST follow EXACTLY this structure:
            
            {
              "name": "<tool-name>",
              "parameters": {
                "query": "<EXACT user question>"
              }
            }
            
            Rules:
            - query MUST be exactly the user question
            - NEVER translate the query
            - NEVER modify the query
            - NEVER shorten the query
            - query must NEVER be null
            
            --------------------------------
            PRIMARY TOOL SELECTION
            --------------------------------
            
   
            
            If the question contains any of these words:
            article, categorie, category, catégorie, voiture
            → search-hybrid-postgreSQL
       
            
            --------------------------------
            MERGING RULE
            --------------------------------
            
            After both tool responses:
            
            - merge documents
            - remove duplicates
            - ignore entries containing only:
              IMAGE :
              Description of the image
            
            --------------------------------
            SUMMARY RULES
            --------------------------------
            
            When writing the summary:
            
            - use ONLY retrieved document text
            - maximum 4 sentences
            - do NOT invent explanations
            - do NOT use external knowledge
            
            If the retrieved information is insufficient:
            
            Return:
            
            {
            "documents": [],
            "images": [],
            "message": "Je n'ai pas trouvé suffisamment d'informations. Pouvez-vous préciser votre demande ?"
            }
            
            --------------------------------
            IMAGE URL RULE
            --------------------------------
            
            Convert:
            
            /alfrescoimages/<filename>
            
            to
            
            http://localhost:8083/api/v1/chat/<filename>
            
            --------------------------------
            FINAL RESPONSE FORMAT
            --------------------------------
            
            Return ONLY:
            
            {
            "documents": [],
            "images": [],
            "message": ""
            }
        """;

    private final Map<String, McpAsyncClient> toolClients = new ConcurrentHashMap<>();

    public MyAiAgent(ChatClient.Builder chatClientBuilder, List<McpAsyncClient> mcpAsyncClients) {
        this.mcpAsyncClients = mcpAsyncClients;
        this.chatClient = chatClientBuilder
                .defaultSystem(DEFAULT_SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder().maxMessages(20).build()
                        ).build()
                )
                .build();
    }

    @PostConstruct
    public void waitForMcpReady() {

        for (McpAsyncClient client : mcpAsyncClients) {
            client.initialize().block();
            var tools = client.listTools().block();
            tools.tools().forEach(tool -> {
                toolClients.put(tool.name(), client);
            });
        }
    }

    public Mono<String> askQuestion(String question) {
        String q = (question == null || question.isBlank()) ? " " : question.trim();
        String toolName = selectTool(q);

        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
                .name(toolName)
                .arguments(Map.of("query", q))
                .build();

        McpAsyncClient client = toolClients.get(toolName);
        if(client == null) {
            return Mono.just("""
                  {
                    "documents": [],
                    "images": [],
                    "message": "Tool non trouvé: %s"
                  }
            """.formatted(toolName));
        }
        return client.callTool(request)
                .map(result -> result.content().stream()
                        .filter(McpSchema.TextContent.class::isInstance)
                        .map(McpSchema.TextContent.class::cast)
                        .map(McpSchema.TextContent::text)
                        .collect(java.util.stream.Collectors.joining("\n\n"))
                )
                .defaultIfEmpty(fallbackMessage());

    }

    private String fallbackMessage() {
        return """
            {
            "documents": [],
            "images": [],
            "message": "Je n'ai pas trouvé suffisamment d'informations. Pouvez-vous préciser votre demande ?"
            }
            """;
    }

    private String selectTool(String q) {
        String query = q.toLowerCase();
        if(query.contains("alfresco") || query.contains("mcp") || query.contains("rag")) {
            return "get-alfresco-informations";
        }
        if(query.contains("voiture") || query.contains("marque") || query.contains("prix")) {
            return "search-hybrid-postgreSQL";
        }
        else {
            return "search-hybrid";
        }
    }

}