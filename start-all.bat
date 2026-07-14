@echo off
setlocal

rem ---------------------------------------------------------------------
rem Starts the full Spring AI MCP demo stack in separate console windows:
rem   1. MCP Authorization Server  (http://localhost:9000)
rem   2. MCP Server                (http://localhost:8082)
rem   3. MCP Client                (http://localhost:8083)
rem   4. MCP Chatbot (Angular)     (http://localhost:4200)
rem
rem Close a window (or Ctrl+C inside it) to stop the corresponding service.
rem ---------------------------------------------------------------------

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

echo ==========================================================
echo  Starting Spring AI MCP Demo stack
echo ==========================================================

echo [1/4] Starting MCP Authorization Server (http://localhost:9000) ...
start "MCP Authorization Server" cmd /k "cd /d "%ROOT%" && gradlew.bat :mcp-authorization-server:bootRun"

echo     Waiting for the authorization server to come up...
timeout /t 15 /nobreak >nul

echo [2/4] Starting MCP Server (http://localhost:8082) ...
start "MCP Server" cmd /k "cd /d "%ROOT%" && gradlew.bat :mcp-server:bootRun"

echo     Waiting for the MCP server to come up...
timeout /t 10 /nobreak >nul

echo [3/4] Starting MCP Client (http://localhost:8083) ...
start "MCP Client" cmd /k "cd /d "%ROOT%" && gradlew.bat :mcp-client:bootRun"

echo [4/4] Starting MCP Chatbot (http://localhost:4200) ...
start "MCP Chatbot" cmd /k "cd /d "%ROOT%\mcp-chatbot" && (if not exist node_modules npm install) && npm start"

echo.
echo All services are starting in separate windows.
echo Close a window (or press Ctrl+C inside it) to stop that service.

endlocal

