package com.mcpclient.clientmcp.controller;

import com.mcpclient.clientmcp.agents.MyAiAgent;
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping(value = "api/v1/chat")
@AllArgsConstructor
public class ChatController {

    private final MyAiAgent myAiAgent;
    private final String IMAGE_DIR = "";

    @GetMapping(value = "/question")
    public Mono<String> ask(@RequestParam String question) {
        return myAiAgent.askQuestion(question);
    }

    @GetMapping(value = "/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        try{
            Path filePath = Paths.get(IMAGE_DIR).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if(!resource.exists()) return ResponseEntity.notFound().build();

            String contentType = Files.probeContentType(filePath);
            if(contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        }
        catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
        catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
