package com.rag.alfresco.tools;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagTool {

    private final Logger logger = LoggerFactory.getLogger(RagTool.class);
    @Autowired
    private ElasticsearchClient elasticsearchClient;
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    public RagTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(name="search-hybrid", description = "Hybrid BM25 + vector search for precise keyword + semantic matching")
    public String hybridSearch(String query) {
        logger.info("[MCP] hybrid: {}", query);

        try {
            float[] embedding = embeddingModel.embed(query); // conversion vectorielle / en vecteur
            List<Float> embeddingList = new ArrayList<>(embedding.length);
            for (float f : embedding) embeddingList.add(f);

            var response = elasticsearchClient.search(
                    s -> s.index("alfresco-ai-document-index")
                            .query(q -> q
                                    .bool(b -> b
                                            .should(
                                                    should -> should
                                                            .match(
                                                                    m -> m
                                                                            .field("text").query(query).boost(Float.valueOf(2.0f))
                                                            )
                                            )
                                            .should(
                                                    should -> should
                                                            .knn(
                                                                    k -> k
                                                                            .field("embedding")
                                                                            .queryVector(embeddingList)
                                                                            .k(20)
                                                                            .numCandidates(100)
                                                            )
                                            )
                                    )
                            ),
                    Map.class
            );

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode(); // creer un objet json vide e.g: {}
            ArrayNode docsArray = mapper.createArrayNode(); // creer un tableau json vide e.g: []

            for (var hit : response.hits().hits()) {
                Map<String, Object> src = hit.source();
                ObjectNode docNode = mapper.createObjectNode(); // creer un objet json vide e.g: {}
                docNode.put("id", String.valueOf(src.get("documentId")));
                docNode.put("path", String.valueOf(src.get("path")));
                docNode.put("title", String.valueOf(src.get("title")));
                docNode.put("text", String.valueOf(src.get("text")));
                docsArray.add(docNode);
            }

            root.set("documents", docsArray);
            root.set("images", mapper.createArrayNode());
            root.put("message", "Resultat de la recherche hybride");

            return root.toString();

        }
        catch(Exception e) {
            logger.error("Error hybrid search", e);
            return "{\"documents\": [],\"images\":[], \"message\": \"Erreur interne recherche hybride\"}";
        }

    }

    @Tool(name="get-alfresco-informations", description = "Get and analyse Alfresco information + hybrid search")
    public String getAlfrescoInformations(@ToolParam(description = "User query") String query) {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode mergedDocs = mapper.createArrayNode();
        ArrayNode mergedImgs = mapper.createArrayNode();

        if(query == null || query.isBlank()) {
            root.put("message", "Erreur : resultat vide");
            root.set("documents", mergedDocs);
            root.set("images", mergedImgs);
            return root.toString();
        }

        logger.info("[MCP] get-alfresco-informations: {}", query);
        Set<String> seenTexts = new LinkedHashSet<>();
        try {
            List<Document> primaryDocs = vectorStore.similaritySearch(query).stream().limit(5).toList();
            for (Document doc : primaryDocs) {
                String rawText = doc.getText();
                List<String> images = extractImages(rawText);
                String cleanedText = cleanText(rawText);

                if (!isValidChunk(cleanedText) || !seenTexts.add(cleanedText)) continue;

                ObjectNode docNode = mapper.createObjectNode();
                docNode.put("id", String.valueOf(doc.getMetadata().getOrDefault("documentId", "")));
                docNode.put("path", String.valueOf(doc.getMetadata().getOrDefault("path", "")));
                docNode.put("title", String.valueOf(doc.getMetadata().getOrDefault("title", "")));
                docNode.put("text", cleanedText);

                ObjectNode meta = mapper.createObjectNode();
                meta.put("type", String.valueOf(doc.getMetadata().getOrDefault("type", "")));
                docNode.set("metadata", meta);

                mergedDocs.add(docNode);

                for (String img : images) {
                    ObjectNode imgNode = mapper.createObjectNode();
                    imgNode.put("id", docNode.get("id").asText() + "_img");
                    imgNode.put("path", convertImageUrl(img));
                    imgNode.put("description", "Image liée au document");

                    mergedImgs.add(imgNode);
                }

            }

            String hybridJson = hybridSearch(query); //BM25 + KNN
            JsonNode hybridNode = mapper.readTree(hybridJson);

            for(JsonNode doc : hybridNode.path("documents")) {
                String text = doc.path("text").asText();
                if(!isValidChunk(text) || !seenTexts.add(text)) continue;

                mergedDocs.add(doc);
            }

            for(JsonNode img : hybridNode.path("images")) {
                mergedImgs.add(img);
            }

            String summary = buildSummary(mergedDocs); // retourne 4 phrases maximum
            if(summary == null || summary.isBlank()) {
                summary = "Aucune reponse !";
            }
            root.set("documents", mergedDocs);
            root.set("images", mergedImgs);
            root.put("message", summary);

        }
        catch (Exception e) {
            logger.info("Erreur au niveau du Tool get-alfresco-informations", e.getMessage());
            logger.info("Erreur exception: ", e);
            root.set("documents", mapper.createArrayNode());
            root.set("images", mapper.createArrayNode());
            root.put("message", "Erreur lors des recherches");
        }

        return root.toString();
    }

    private boolean isValidChunk(String text) {
        if(text == null) return false;
        String cleaned = text.trim();
        return cleaned.length() > 40 && !cleaned.contains("IMAGE :") && !cleaned.contains("Description of the image");
    }

    private List<String> extractImages(String text) {
        List<String> images = new ArrayList<>();
        if(text == null) return images;
        Pattern p = Pattern.compile("/alfrescoimages/\\S+\\.png"); // /alfrescoimages/images_13223.png
        Matcher m = p.matcher(text);
        while (m.find()) images.add(m.group());
        return images;
    }

    private String convertImageUrl(String imgPath) {
        return "http://localhost:8083/api/v1/chat/" + imgPath.replace("/alfrescoimages/", "");
    }

    private String cleanText(String text) {
        if(text == null) return "";
        return text.replaceAll("(?i)IMAGE :.*", "")
                .replaceAll("(?i)Description of the image.*", "")
                .replaceAll("(?i)DOCUMENT SOURCE :.*", "")
                .trim();
    }

    //limiter à 4 phrases le texte
    private String buildSummary(ArrayNode docs) {
        StringBuilder sb = new StringBuilder();
        int sentenceCount = 0;
        for(JsonNode doc : docs) {
            String text = doc.path("text").asText();
            String[] sentences = text.split("(?<=[.!?])\\s+"); // Salut. Je voudrais une description de l'image!
            for(String s : sentences) {
                if(sentenceCount >= 4) break;
                sb.append(s.trim()).append(" ");
                sentenceCount++;
            }
            if(sentenceCount >= 4) break;
        }
        return sb.toString().trim();
    }

}
