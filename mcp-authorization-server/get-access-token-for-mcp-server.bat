@echo off
rem Gets an access token for the mcp client (via the real end-user login +
rem authorization_code/PKCE flow for mcp-chatbot-client) and then exchanges it
rem twice (RFC 8693 OAuth 2.0 Token Exchange): first adding mcp-client-client
rem as an actor, then adding mcp-server-client as a second actor on top, so
rem the resulting access token carries the whole actor chain (sub=end user,
rem act.sub=mcp-server-client, act.act.sub=mcp-client-client).
rem All the HTTP/cookie/PKCE logic lives in the companion PowerShell script.
rem
rem Usage: get-access-token-for-mcp-server.bat [username] [password]
rem   e.g. get-access-token-for-mcp-server.bat jane@doe.com jane

setlocal
set "USERNAME_ARG=%~1"
set "PASSWORD_ARG=%~2"
if "%USERNAME_ARG%"=="" set "USERNAME_ARG=john@doe.com"
if "%PASSWORD_ARG%"=="" set "PASSWORD_ARG=john"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0get-access-token-for-mcp-server.ps1" -Username "%USERNAME_ARG%" -Password "%PASSWORD_ARG%"
set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%

