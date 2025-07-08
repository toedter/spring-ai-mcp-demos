# Spring AI MCP Demos

Several MCP demos (like an mcp server) written in Java with Spring Boot 3.5.x and Spring AI.

## How to run the mcp server

1. **Build the MCP server**

   ```sh
   ./gradlew build
   ```

2. **Download and install Claude Desktop**

    - Go to [https://claude.ai/download](https://claude.ai/download)
    - Download the latest release for your operating system and extract it.
    - Make sure to disable Web access in the settings of Claude Desktop.

3. **Configure Claude Desktop**

    - Edit the `claude_desktop_config.json` file.
    - Add or update the `mcpServers` section to include:

      ```json
      {
        "mcpServers": {
          "demo-local": {
            "command": "java",
            "args": [
              "-jar",
              "D:\\dev\\git\\spring-ai-mcp-demos\\mcp-server\\build\\libs\\mcp-server-0.0.1-SNAPSHOT.jar",
              "--spring.profiles.active=stdio"
            ]
          }
        }
      }
      ```

      Adjust the path to the JAR file as needed. The example shows a Windows config.
      Then copy the file to the appropriate `claude-desktop` directory.
      Where to put in using different operating systems, you find at https://modelcontextprotocol.io/quickstart/user.

4. **Start Claude Desktop**

    - Launch Claude Desktop.
    - Select the configured MCP server and start it.

5. **Use the demo**

    - Interact with the MCP server through Claude Desktop’s UI.
    - In Claude Desktop you can ask questions like:
      - `From which movies is Kai's favorite quote?`
    - To integrate with a movie demo server, follow the steps below.

      - Clone the movie demo repository:
        ```sh
        git clone https://github.com/toedter/spring-hateoas-jsonapi.git
        cd spring-hateoas-jsonapi
        ./gradlew bootRun
        ```
      - The movie demo server will start on `http://localhost:8080`.
      - In Claude Desktop you can now ask questions like:
          - `List all movies directed by Quentin Tarantino.`
          - `Which movies directed by Christopher Nolan are in the top ten?`
  
## License

Apache 2.0


