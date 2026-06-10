package com.mcppostgres.mcppostgresql.controller;

import com.mcppostgres.mcppostgresql.service.DocumentService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@AllArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @CrossOrigin(origins = "http://localhost:4200")
    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        try {
            documentService.saveDocument(file);
            return ResponseEntity.status(HttpStatus.OK).body("Enregistrement réussi !");
        }
        catch (Exception e) {
            return ResponseEntity.status(500).body("Erreur: " + e.getMessage());
        }
    }

}
