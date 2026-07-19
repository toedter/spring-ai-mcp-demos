# mcp-client compliance with the MCP client guidelines

This maps mcp-client's implementation to each guideline in
[`backup/mcp-client.md`](../backup/mcp-client.md). It's a demo-scale summary,
not a formal audit.

## 1. Keep a human in the loop for actions

Every tool call is wrapped by `ApprovalToolCallback`, which pushes an
`approval` SSE event to the chatbot and blocks (via `ApprovalRegistry`) until
the user approves or denies it. Endpoints with no interactive channel
(`GET /chat`, `POST /api/chat`) **fail closed**: `ApprovalToolCallback`
refuses the tool call outright instead of running it unapproved. Approved
results are also size-capped (`ApprovalToolCallback.MAX_RESULT_LENGTH`) so a
misbehaving server can't flood the model's context.

A separate persistent activity log is not implemented; the streamed
`status`/`approval` events already show each tool call, its arguments, and
outcome inline in the chat.

`ToolApprovalContext` (used by the tool, sampling, and elicitation handlers
to reach the active SSE stream) tracks the active session on a shared stack
rather than a `ThreadLocal`, because the MCP SDK dispatches sampling and
elicitation callbacks on its own transport thread, not the worker thread the
streaming endpoint runs on. This is correct for this single-user demo; a
multi-tenant deployment would need explicit request correlation, which the
MCP Java SDK's callback signatures don't currently carry.

## 2. Sampling MUST be gated by user approval

`SamplingApprovalHandler` (`@McpSampling`) pushes a `sampling` SSE event
summarizing the request and blocks on the same `ApprovalRegistry` used for
tool calls. mcp-client — not the server — owns the actual model call, via
the same `ChatClient` configured for the main chat flow. Requests received
outside an interactive session fail closed. Editing the request/response
before it's returned to the server (a SHOULD) is not implemented; only
binary approve/deny is supported in this demo.

## 3. Elicitation MUST NOT collect secrets; responses MUST be validated

`ElicitationApprovalHandler` (`@McpElicitation`) renders the server's
message and flat JSON Schema to the user via an `elicitation` SSE event, and
the chatbot's generic schema-driven form renders the appropriate input per
field type (string/number/boolean/enum). Any schema whose property
name/title/description suggests a credential (password, secret, API key,
token, ...) is **auto-declined without ever prompting the user**. Only
`form`-mode requests are supported; `url`-mode requests are declined. Values
are only returned to the server after the user explicitly submits, declines,
or cancels.

## 4. Treat server content as untrusted

Tool/sampling/elicitation payloads are rendered as inert text/JSON in the
Angular UI (Angular's default interpolation, no `innerHTML`), never
interpreted as instructions. mcp-client does not auto-execute anything found
inside tool output.

## 5. Mitigate SSRF when fetching OAuth discovery URLs — N/A

mcp-client doesn't perform OAuth metadata *discovery*: the authorization
server's `issuer-uri`/`jwk-set-uri` and the token/token-exchange endpoints
are static, checked-in configuration values (`application.yaml`), not
runtime-supplied or server-supplied URLs. There is no attacker-controlled
discovery surface to defend against SSRF.

## 6. Handle tokens and credentials securely

Client-credentials secrets live in `application.yaml` (fine for this local
demo; would move to a secret store in production). Access tokens are
short-lived and refreshed early (`CachedToken` in `McpServiceTokenProvider`
and `TokenExchangeService`). Tokens are **never reused across MCP servers**:
both the client-credentials token cache and the RFC 8693 token-exchange
cache are keyed by MCP server connection name (`McpTransportConfig` threads
`serverName` through to both), so each server connection gets an
independent token even though this demo only configures one server.

## 7. Verify and pin the servers you trust

The one configured MCP server connection (`weather-x-mcp-server`) is a
static, checked-in URL in `application.yaml`, not dynamically discovered or
user-suppliable at runtime — there's no spoofing surface a hash/cert-pinning
mechanism would add protection against here. Roots are not applicable: this
client only talks to remote HTTP MCP servers, not a filesystem-backed
server, so there's no directory boundary to communicate.

## 8. Enforce timeouts and resource limits

The MCP client's request timeout (`spring.ai.mcp.client.request-timeout`) is
raised from Spring AI's 20s default to 5 minutes, to match how long a tool
call can legitimately take here: a tool call to mcp-server can itself nest a
sampling or elicitation round-trip back through this client, and each of
those is gated by the same 5-minute human-approval window
(`ApprovalRegistry.APPROVAL_TIMEOUT`) — a 20s outer timeout would abort the
whole chain long before a human has a chance to respond. `ApprovalRegistry`
bounds how long any single approval/response wait blocks (then fails
closed/cancels). `ApprovalToolCallback` additionally caps approved
tool-result size before it reaches the model. The HTTP transport applies its
own connect timeout independently.

## 9. Scale tool exposure with progressive discovery — N/A at this scale

This demo exposes 6 tools total, nowhere near the point (context share of
1-5%) where progressive discovery would help; all tool definitions are
loaded upfront.

## 10. Negotiate protocol and capabilities during initialization

The MCP Java SDK performs the `initialize` handshake automatically
(`spring.ai.mcp.client.initialized: true`, the default). mcp-client only
advertises the capabilities it actually implements — tool calling plus, now,
sampling and elicitation via the `@McpSampling`/`@McpElicitation`
annotation-driven registry (`ClientMcpSyncHandlersRegistry`), which is
autoconfigured to report these capabilities during `initialize` only when a
handler bean is present.
