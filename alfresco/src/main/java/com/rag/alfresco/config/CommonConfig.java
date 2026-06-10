package com.rag.alfresco.config;

import com.rag.alfresco.tools.RagTool;
import io.micrometer.observation.ObservationRegistry;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonConfig {

    @Bean
    public ToolCallbackProvider tools(RagTool ragTool) {
        return MethodToolCallbackProvider.builder().toolObjects(ragTool).build();
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder(new HttpHost("localhost", 9200, "http")).build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        var ollamaApi = OllamaApi.builder()
                .baseUrl("http://localhost:11434")
                .build();
        return new OllamaEmbeddingModel(
                ollamaApi,
                OllamaOptions.builder().model("nomic-embed-text").build(),
                ObservationRegistry.NOOP,
                ModelManagementOptions.defaults()
                );
    }

    @Bean
    public ElasticsearchVectorStore vectorStore(RestClient restClient, EmbeddingModel embeddingModel) {
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName("alfresco-ai-document-index");
        options.setEmbeddingFieldName("embedding");
        options.setSimilarity(SimilarityFunction.cosine);
        options.setDimensions(768);

        return ElasticsearchVectorStore.builder(restClient, embeddingModel)
                .options(options)
                .initializeSchema(true)
                .batchingStrategy(new TokenCountBatchingStrategy())
                .build();
    }

}
