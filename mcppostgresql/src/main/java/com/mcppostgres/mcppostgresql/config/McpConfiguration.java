package com.mcppostgres.mcppostgresql.config;

import com.mcppostgres.mcppostgresql.tools.PostgresService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfiguration {

    @Bean
    public ToolCallbackProvider toolCallbackProvider(PostgresService postgresService) {
        return MethodToolCallbackProvider.builder().toolObjects(postgresService).build();
    }

}
