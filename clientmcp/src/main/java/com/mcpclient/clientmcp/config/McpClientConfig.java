package com.mcpclient.clientmcp.config;

import io.modelcontextprotocol.client.McpAsyncClient;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class McpClientConfig {

    @Bean
    @Primary
    public AsyncMcpToolCallbackProvider asyncMcpToolCallbackProvider(List<McpAsyncClient> mcpClients) {
        return new AsyncMcpToolCallbackProvider(mcpClients);
    }

}
