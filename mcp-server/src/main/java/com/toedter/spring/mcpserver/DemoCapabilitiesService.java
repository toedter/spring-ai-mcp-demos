/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.toedter.spring.mcpserver;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.SamplingMessage;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * Demo tools that exercise mcp-client's sampling and elicitation support end
 * to end, so those MCP client capabilities can be verified through the real
 * chatbot rather than only with unit tests.
 */
@Service
public class DemoCapabilitiesService {

    @McpTool(name = "write_haiku_about_weather",
            description = "Writes a short haiku about the weather in a city, generated via the client's LLM "
                    + "(MCP sampling).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
    public String writeHaikuAboutWeather(
            @McpToolParam(description = "The city to write the haiku about") String city,
            McpSyncServerExchange exchange) {

        SamplingMessage message = new SamplingMessage(Role.USER,
                TextContent.builder("Write a short haiku about the weather in " + city + ".").build());
        CreateMessageRequest request = CreateMessageRequest.builder(List.of(message), 200)
                .systemPrompt("You are a poet who writes concise, evocative haikus.")
                .build();

        CreateMessageResult result = exchange.createMessage(request);
        Content content = result.content();
        return content instanceof TextContent textContent ? textContent.text() : "[non-text response]";
    }

    @McpTool(name = "get_preferred_temperature_unit",
            description = "Asks the user which temperature unit they prefer, via MCP elicitation.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
    public String getPreferredTemperatureUnit(McpSyncServerExchange exchange) {
        Map<String, Object> requestedSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "unit", Map.of(
                                "type", "string",
                                "title", "Preferred temperature unit",
                                "enum", List.of("celsius", "fahrenheit"))),
                "required", List.of("unit"));

        ElicitFormRequest elicitRequest = ElicitFormRequest
                .builder("What temperature unit do you prefer?", requestedSchema)
                .build();

        ElicitResult result = exchange.createElicitation(elicitRequest);
        if (result.action() != ElicitResult.Action.ACCEPT || result.content() == null) {
            return "The user did not provide a preferred temperature unit.";
        }
        Object unit = result.content().get("unit");
        return unit == null ? "The user did not provide a preferred temperature unit." : String.valueOf(unit);
    }

}
