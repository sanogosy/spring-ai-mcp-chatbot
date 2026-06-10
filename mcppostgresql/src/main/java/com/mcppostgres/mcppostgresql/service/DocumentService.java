package com.mcppostgres.mcppostgresql.service;

import com.mcppostgres.mcppostgresql.repository.DocumentRepository;
import com.mcppostgres.mcppostgresql.tools.PostgresService;
import lombok.AllArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private final DocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;

    public void saveDocument(MultipartFile file) throws IOException {
        byte[] fileBytes = file.getBytes();

        PDDocument doc = Loader.loadPDF(fileBytes);
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(doc);
        doc.close();

        try {
            List<String> chunks = splitText(text, 1000);
            for(String chunck: chunks) {
                float[] embedding = embeddingModel.embed(chunck);
                String pgVector = toPgVector(embedding);
                documentRepository.saveDocument(chunck, pgVector, "{}");
            }
        } catch (Exception e) {
            log.error("Error save document " + e.getMessage());
        }
    }

    public String toPgVector(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for(int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if(i < embedding.length - 1) sb.append(",");
        }
        sb.append("]");

        return sb.toString();
    }

    public List<String> splitText(String text, int chunckSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        for(int i = 0; i < length; i += chunckSize) {
            chunks.add(text.substring(i, Math.min(length, i + chunckSize)));
        }
        return chunks;
    }

}
