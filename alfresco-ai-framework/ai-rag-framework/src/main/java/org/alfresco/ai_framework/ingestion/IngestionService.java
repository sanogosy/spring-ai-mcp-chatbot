package org.alfresco.ai_framework.ingestion;

import org.alfresco.ai_framework.utils.FileConverter;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Service for ingesting documents into the vector store, utilizing document parsing and transformation.
 */
@Service
public class IngestionService {

    private final ChatClient chatClient;

    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;

    public IngestionService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {

        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Ingests a document into the vector store by reading, transforming, and storing it.
     */
    public void ingest(String documentId, String folderId, String fileName, Resource file) throws IOException {
        logger.info("Starting ingestion for document ID: {}, folder: {}", documentId, folderId);

//        List<Document> documents = transformDocument(file);
        List<Document> documents = loadDataIntoVectorStore(file, documentId, folderId, fileName);
        addMetadata(documents, documentId, folderId, fileName);

        List<Document> processedDocs = DocumentSplitter.splitLargeDocuments(documents);

        deleteByDocumentId(documentId);
        vectorStore.add(processedDocs);

        logger.info("Ingestion complete for document ID: {}", documentId);
    }

    public List<Document> loadDataIntoVectorStore(Resource resource, String documentId, String folderId, String fileName) throws IOException, IOException {
        logger.info("Ingestion loadDataIntoVectorStore");
        List<Document> documentList = new ArrayList<>();

        InputStream inputStream = resource.getInputStream();
        //Convert any inputStream file type pptx, jpeg, png, doc, docx to pdf
        File tempFile = FileConverter.convertToPdf(inputStream, fileName);

        if (tempFile.length() == 0) {
            // The file is empty
            System.out.println("The generated PDF file is empty.");
        } else {
            // The file contains data
            System.out.println("The generated PDF file is not empty. Size: " + tempFile.length() + " bytes.");
        }

        PDDocument document = PDDocument.load(tempFile);
        PDPageTree pdPages = document.getPages();
        PDFTextStripper pdfTextStripper = new PDFTextStripper();
        int index = 0;
        int page = 0;
        for (PDPage pdPage : pdPages) {
            ++page;
            logger.info("Ingestion loadDataIntoVectorStore Page: {}", page);
            pdfTextStripper.setStartPage(page);
            pdfTextStripper.setEndPage(page);
            PDResources resources = pdPage.getResources();
            List<String> media = new ArrayList<>();
            String textContent = pdfTextStripper.getText(document);

            if (resources.getXObjectNames() != null) {
                logger.info("Ingestion loadDataIntoVectorStore resources found");
                for (var c : resources.getXObjectNames()) {
                    logger.info("Ingestion loadDataIntoVectorStore resources getXObjectNames");
                    PDXObject pdxObject = resources.getXObject(c);
                    if (pdxObject instanceof PDImageXObject image) {
                        ++index;
                        BufferedImage imageImage = image.getImage();
                        String directoryPath = "/alfrescoimages"; // A fixed path inside the container
                        File directory = new File(directoryPath);
                        if (!directory.exists()) {
                            directory.mkdirs();
                        }
                        LocalDateTime myDateObj = LocalDateTime.now();
                        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH-mm-ss");

                        String formattedDate = myDateObj.format(dateFormatter);
                        String formattedTime = myDateObj.format(timeFormatter);

                        String imagePath = directoryPath + "/page_" + page + "_im_" + index + "_" + formattedDate + "_" + formattedTime + ".png";
                        logger.info("Ingestion loadDataIntoVectorStore imagePath: {}", imagePath);
                        FileOutputStream fileOutputStream = new FileOutputStream(imagePath);
                        ImageIO.write(imageImage, "png", fileOutputStream);
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        ImageIO.write(imageImage, "png", byteArrayOutputStream);
                        byte[] data = byteArrayOutputStream.toByteArray();
                        media.add(imagePath);
                        // Process media content
//                        String msg = """
//                                Read every information into image.
//                                If possible generate a compact description that explains only what is visible.
//                                """;
//                        Resource imageResource = new ByteArrayResource(data);
//                        Media imageMedia = new Media(MimeType.valueOf("image/png"), imageResource);
//
                        String imageDescription = "";
//                        UserMessage um = new UserMessage(msg, imageMedia);
//                        String imageDescription = chatClient.prompt(new Prompt(um))
//                                .call()
//                                .content();

                        System.out.println(imageDescription);
                        textContent = textContent + "\n" + "IMAGE : " + imagePath + "\n" + "Description of the image :\n" + imageDescription;
                        logger.info("1-Ingestion loadDataIntoVectorStore textContent: {}", textContent);
                    }
                    Map<String, Object> metadata = new HashMap();
                    metadata.put("Page", page);
                    metadata.put("media", media);

                    textContent = textContent + "\n" + "DOCUMENT SOURCE : " + documentId + "/" + folderId + "/" + fileName + "\n";
                    logger.info("Ingestion loadDataIntoVectorStore textContent: {}", textContent);
                    Document pageDoc = new Document(textContent, metadata);
                    documentList.add(pageDoc);
                }
//                if(documentList.isEmpty()){
//                    Map<String, Object> metadata = new HashMap();
//                    metadata.put("Page", page);
//                    metadata.put("media", media);
//
//                    textContent = textContent + "\n" + "DOCUMENT SOURCE : " + documentId + "/" + folderId + "/" + fileName + "\n";
//
//                    Document pageDoc = new Document(textContent, metadata);
//                    documentList.add(pageDoc);
//                }
                logger.info("Ingestion loadDataIntoVectorStore documentList-2: {}", documentList.size());
            }
            else {
                logger.info("2-Ingestion loadDataIntoVectorStore resource not found");
                Map<String, Object> metadata = new HashMap();
                metadata.put("Page", page);
                metadata.put("media", media);

                textContent = textContent + "\n" + "DOCUMENT SOURCE : " + documentId + "/" + folderId + "/" + fileName + "\n";

                Document pageDoc = new Document(textContent, metadata);
                documentList.add(pageDoc);
            }
            logger.info("Ingestion loadDataIntoVectorStore documentList-1: {}", documentList.size());
        }
        tempFile.deleteOnExit();
        logger.info("Ingestion loadDataIntoVectorStore documentList: {}", documentList.size());
        return documentList;
    }

    /**
     * Deletes documents from the vector store matching the specified document ID.
     */
    public void deleteByDocumentId(String documentId) {
        deleteDocuments("documentId", documentId);
    }

    /**
     * Deletes documents from the vector store matching the specified folder ID.
     */
    public void deleteByFolderId(String folderId) {
        deleteDocuments("folderId", folderId);
    }

    /**
     * Reads and transforms a document from the provided file resource.
     */
    private List<Document> transformDocument(Resource file) {
        List<Document> documentText = new TikaDocumentReader(file).get();

//        List<Document> listDocumentText = documentText;
//
//        listDocumentText.forEach(doc -> {
//            String imagePath = "";
//            if (doc.isText()) {
//                String textContent = doc.getText();
//                // Process text content
//            } else {
//                logger.info("****************************");
//                logger.info("Ingestion for document Media");
//                Media mediaContent = doc.getMedia();
//                // Process media content
//                String msg = """
//                Explain what do you see on the image.
//                Generate a compact description that explains only what is visible.
//                """;
//
//                UserMessage um = new UserMessage(msg, mediaContent);
//                String content = chatClient.prompt(new Prompt(um))
//                        .call()
//                        .content();
//
//                BufferedImage imageImage = createImageFromBytes(mediaContent.getDataAsByteArray());
//                imagePath = "./alfrescoimages/doc_" + doc.getId() + "_im_" + mediaContent.getName() + ".png";
//                FileOutputStream fileOutputStream = null;
//                try {
//                    fileOutputStream = new FileOutputStream(imagePath);
//                } catch (FileNotFoundException e) {
//                    throw new RuntimeException(e);
//                }
//                try {
//                    ImageIO.write(imageImage, "png", fileOutputStream);
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//                try {
//                    ImageIO.write(imageImage, "png", byteArrayOutputStream);
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//                byte[] data = byteArrayOutputStream.toByteArray();
//                String imageBase64 = Base64.getEncoder().encodeToString(data);
//
//                Document textDoc = Document.builder()
//                        .text("Image: " + imagePath + "\nDescription: " + content + "\n")
//                        .build();
//                logger.info("Ingestion for document Media Description: {}", content);
////                textDoc.getMetadata().put("documentId", documentId);
////                textDoc.getMetadata().put("folderId", folderId);
////                textDoc.getMetadata().put("fileName", fileName);
//                documentText.add(textDoc);
//            }
//        });

        return TokenTextSplitter.builder().build().apply(documentText);
    }

    private BufferedImage createImageFromBytes(byte[] imageData) {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
        try {
            return ImageIO.read(bais);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds metadata to each document.
     */
    private void addMetadata(List<Document> documents, String documentId, String folderId, String fileName) {
        documents.forEach(doc -> {
            doc.getMetadata().put("documentId", documentId);
            doc.getMetadata().put("folderId", folderId);
            doc.getMetadata().put("fileName", fileName);
        });
    }

    /**
     * Deletes documents from the vector store that match the specified metadata key and value.
     */
    private void deleteDocuments(String key, String value) {
        logger.info("Deleting documents with {}: {}", key, value);

        try {
            List<Document> documents = vectorStore.similaritySearch(
                    SearchRequest.builder().filterExpression("'" + key + "' == '" + value + "'").build()
            );

            if (!documents.isEmpty()) {
                vectorStore.delete(documents.stream().map(Document::getId).collect(Collectors.toList()));
                logger.info("Deleted {} document(s) with {}: {}", documents.size(), key, value);
            } else {
                logger.info("No documents found with {}: {}", key, value);
            }
        } catch (RuntimeException e) {
            logger.error("Error deleting documents with {}: {}", key, value, e);
        }
    }
}
