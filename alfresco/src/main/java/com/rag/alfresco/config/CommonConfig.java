package com.rag.alfresco.config;

import com.rag.alfresco.tools.RagTool;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
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
    public ElasticsearchVectorStore vectorStore(RestClient restClient, EmbeddingModel embeddingModel) {
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName("alfresco-ai-document-index");
        options.setEmbeddingFieldName("embedding");
        options.setSimilarity(SimilarityFunction.cosine);
        options.setDimensions(1536);

        return ElasticsearchVectorStore.builder(restClient, embeddingModel)
                .options(options)
                .initializeSchema(true)
                .batchingStrategy(new TokenCountBatchingStrategy())
                .build();
    }

}
