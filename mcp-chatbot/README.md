# MCP Chatbot (Angular + deep-chat)
An Angular single-page app that logs a user in against the **mcp-authorization-server**
(OpenID Connect, Authorization Code + PKCE) and then chats with the **mcp-client**
through the [deep-chat](https://deepchat.dev/) web component.
## Features
- OpenID Connect login via `angular-oauth2-oidc` (Authorization Code + PKCE).
- Top toolbar showing the logged-in user (name + email + avatar) on the right.
- Chat UI powered by `<deep-chat>`, connected to the mcp-client only **after** login.
- The OAuth access token is sent to the mcp-client as a `Bearer` token.
## Prerequisites
Start the backends first (from the repository root):
```powershell
# 1. Authorization server (port 9000)
.\gradlew.bat :mcp-authorization-server:bootRun
# 2. MCP client (port 8083) - requires the SIEMENS_LLM_API_KEY env var
#    and the mcp-server jar built (.\gradlew.bat :mcp-server:bootJar)
.\gradlew.bat :mcp-client:bootRun
```
## Run the Angular app
```powershell
cd mcp-chatbot
npm install       # first time only
npm start         # serves on http://localhost:4200
```
Open http://localhost:4200 and click **Sign in**. Log in with the demo user:
| Field    | Value          |
| -------- | -------------- |
| Username | `john@doe.com` |
| Password | `john`         |
After a successful login you are redirected back to the app, the toolbar shows
**John Doe**, and the deep-chat window connects to the mcp-client so you can start
chatting.
## Configuration
All endpoints are defined in `src/app/config.ts`:
- `issuer` - authorization server (`http://localhost:9000`)
- `clientId` - `mcp-chatbot-client` (public PKCE client)
- `MCP_CHAT_URL` - mcp-client deep-chat endpoint (`http://localhost:8083/api/chat`)
## How it fits together
```
Angular (deep-chat)  --1. OIDC login (code+PKCE)-->  mcp-authorization-server (:9000)
Angular (deep-chat)  <-- id/access token ----------  mcp-authorization-server (:9000)
Angular (deep-chat)  --2. POST /api/chat (+Bearer)-> mcp-client (:8083) --> mcp-server / OpenAI
```
