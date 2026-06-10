package com.mcppostgres.mcppostgresql.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PostgresService {

    private static final Logger log = LoggerFactory.getLogger(PostgresService.class);
    private final VectorStore vectorStore;

    @Tool(name = "search-hybrid-postgreSQL", description = "Get general informations")
    public String searchHybrid(String query) {

        log.info("CALLED searchHybrid()");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode mergedDocs = mapper.createArrayNode();
        ArrayNode mergedImgs = mapper.createArrayNode();

        query = (query == null) ? "" : query.trim();
        if (query.isBlank()) return "";

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(2)
                .similarityThreshold(0.6)
                .build();

        List<Document> results;
        try {
            results = vectorStore.similaritySearch(request);
        } catch (Exception e) {
            log.error("Error search-hybrid PostgreSQL " + e.getMessage());
            return "Erreur lors de la recherche de similarite";
        }

        if(results == null || results.isEmpty()) return "Aucun resultat trouvé";

        StringBuilder sb = new StringBuilder();
        for (Document doc: results) {
            String clean = doc.getText()
                    .replace("\r", "")
                    .replace("\\n", "\n")
                    .replace("\n\n", "\n");
            sb.append(clean).append(" ");

            ObjectNode docNode = mapper.createObjectNode();
            docNode.put("id", String.valueOf(doc.getMetadata().getOrDefault("documentId", "")));
            docNode.put("path", String.valueOf(doc.getMetadata().getOrDefault("path", "")));
            docNode.put("title", String.valueOf(doc.getMetadata().getOrDefault("title", "")));
            docNode.put("text", sb.toString());

            ObjectNode meta = mapper.createObjectNode();
            meta.put("type", String.valueOf(doc.getMetadata().getOrDefault("type", "")));
            docNode.set("metadata", meta);

            mergedDocs.add(docNode);

            ObjectNode imgNode = mapper.createObjectNode();
            imgNode.put("id", docNode.get("id").asText() + "_img");
            imgNode.put("path", "");
            imgNode.put("description", "Image liée au document");
            mergedImgs.add(imgNode);
        }

        String summary = buildSummary(mergedDocs); // 4 phrases max, texte réel uniquement
        if(summary == null || summary.isBlank()) {
            summary = "Je n'ai pas trouvé suffisamment d'informations. Pouvez-vous préciser votre demande ?";
        }
        root.set("documents", mergedDocs);
        root.set("images", mergedImgs);
        root.put("message", summary);

        return root.toString();
    }

    private String buildSummary(ArrayNode docs) {
        // Limiter à 4 phrases maximum, texte réel uniquement
        StringBuilder sb = new StringBuilder();
        int sentenceCount = 0;
        for (JsonNode doc : docs) {
            String text = doc.path("text").asText();
            String[] sentences = text.split("(?<=[.!?])\\s+");
            for (String s : sentences) {
                if (sentenceCount >= 4) break;
                sb.append(s.trim()).append(" ");
                sentenceCount++;
            }
            if (sentenceCount >= 4) break;
        }
        return sb.toString().trim();
    }

}