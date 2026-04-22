package com.toedter.spring.mcpclient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class ChatControllerTest {
    private MockMvc mockMvc;
    private ChatClient.Builder chatClientBuilder;
    private ChatClient.CallResponseSpec callResponseSpec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        var requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        var chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenReturn(requestSpec);

        var builderWithSystem = mock(ChatClient.Builder.class);
        when(builderWithSystem.defaultToolCallbacks(any(ToolCallbackProvider.class))).thenReturn(builderWithSystem);
        when(builderWithSystem.build()).thenReturn(chatClient);

        chatClientBuilder = mock(ChatClient.Builder.class);
        when(chatClientBuilder.defaultSystem(any(String.class))).thenReturn(builderWithSystem);

        ToolCallbackProvider tools = mock(ToolCallbackProvider.class);
        ChatController controller = new ChatController(chatClientBuilder, tools);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }
    @Test
    void chatWithoutMessageReturnsTip() throws Exception {
        mockMvc.perform(get("/chat"))
                .andExpect(status().isOk())
                .andExpect(content().string("Please provide 'message' parameter"));
    }
    @Test
    void chatWithMessageReturnsAnswer() throws Exception {
        when(callResponseSpec.content()).thenReturn("Hello, world!");

        mockMvc.perform(get("/chat").param("message", "Say hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, world!"));
    }

    // ---------------------------------------------------------------
}
