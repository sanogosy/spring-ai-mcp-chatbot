package org.alfresco.ai_framework.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.commons.io.IOUtils;

public class FileConverter {

    public static File convertToPdf(InputStream inputStream, String originalFilename) throws IOException {

        MimeType mimeType = MimeType.valueOf(Files.probeContentType(new File(originalFilename).toPath()));
        File tempPdfFile = File.createTempFile("converted", ".pdf");

        try (FileOutputStream out = new FileOutputStream(tempPdfFile)) {
            switch (mimeType.getType() + "/" + mimeType.getSubtype()) {
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": // .docx
                    convertDocxToPdf(inputStream, out);
                    break;
                case "image/png":
                case "image/jpeg":
                    convertImageToPdf(inputStream, out);
                    break;
                case "application/pdf":
                    // If it's already a PDF, just copy the stream.
                    IOUtils.copy(inputStream, out);
                    break;
                // Add more cases for .ppt, .pptx, .doc, etc.
                default:
                    throw new UnsupportedOperationException("Unsupported file type: " + mimeType);
            }
        }
        return tempPdfFile;
    }

    private static void convertDocxToPdf(InputStream inputStream, FileOutputStream outputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            PdfOptions options = PdfOptions.create();
            PdfConverter.getInstance().convert(document, outputStream, options);
        }
    }

    private static void convertImageToPdf(InputStream inputStream, FileOutputStream outputStream) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, IOUtils.toByteArray(inputStream), null);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.drawImage(pdImage, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            contentStream.close();
            document.save(outputStream);
        }
    }
}