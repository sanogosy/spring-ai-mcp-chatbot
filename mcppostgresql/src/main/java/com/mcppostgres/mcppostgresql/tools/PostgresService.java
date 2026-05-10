package com.mcppostgres.mcppostgresql.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class PostgresService {

    @Tool(name = "get-user-informations", description = "Return user informations")
    public String getUserList() {
        return "";
    }

}
