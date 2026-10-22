[CmdletBinding()]
param(
  [string]$AuthServer = "http://localhost:9000",
  [string]$Username = "john@doe.com",
  [string]$Password = "john",
  [string]$ChatbotClientId = "mcp-chatbot-client",
  [string]$RedirectUri = "http://localhost:4200",
  [string]$Scope = "openid profile mcp.tools mcp.tools.movies mcp.tools.weather mcp.tools.diagnostics",
  [string]$McpClientId = "mcp-client-client",
  [string]$McpClientSecret = "mcp-client-secret"
)

# Gets a real end-user access token (via the mcp-chatbot-client
# authorization_code + PKCE flow, logging in as a demo user) and then
# exchanges it (RFC 8693 OAuth 2.0 Token Exchange) for a delegated access
# token that mcp-server will accept, with mcp-client-client added as the
# "act" (actor) claim while the user's "sub" claim is preserved. This
# mirrors TokenExchangeService.exchangeUserToken(...) in mcp-client.
#
# A client-credentials token alone cannot be used as the subject_token here:
# the authorization server only accepts a real, previously-issued user
# authorization as the subject of a token exchange (see
# TokenExchangeIntegrationTest), so this script performs the full
# authorization-code dance instead of taking a shortcut.

$ErrorActionPreference = "Stop"

function Get-WebResponse {
  param(
    [string]$Uri,
    [string]$Method = "GET",
    [System.Net.CookieContainer]$CookieJar,
    [string]$FormBody
  )
  $request = [System.Net.HttpWebRequest]::Create($Uri)
  $request.Method = $Method
  $request.CookieContainer = $CookieJar
  $request.AllowAutoRedirect = $false
  $request.Timeout = 10000
  if ($FormBody) {
    $request.ContentType = "application/x-www-form-urlencoded"
    $bytes = [Text.Encoding]::UTF8.GetBytes($FormBody)
    $request.ContentLength = $bytes.Length
    $stream = $request.GetRequestStream()
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Close()
  }
  try {
    return $request.GetResponse()
  } catch [System.Net.WebException] {
    if ($_.Exception.Response) { return $_.Exception.Response }
    throw
  }
}

function Read-ResponseBody {
  param([System.Net.WebResponse]$Response)
  $reader = New-Object IO.StreamReader($Response.GetResponseStream())
  try { return $reader.ReadToEnd() } finally { $reader.Close() }
}

function New-CodeVerifier {
  $bytes = New-Object byte[] 32
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($bytes)
  [Convert]::ToBase64String($bytes) -replace '\+', '-' -replace '/', '_' -replace '=', ''
}

function New-CodeChallenge([string]$verifier) {
  $sha256 = [Security.Cryptography.SHA256]::Create()
  $hash = $sha256.ComputeHash([Text.Encoding]::ASCII.GetBytes($verifier))
  [Convert]::ToBase64String($hash) -replace '\+', '-' -replace '/', '_' -replace '=', ''
}

$cookieJar = New-Object System.Net.CookieContainer

Write-Host "Step 1/5: loading the login page to obtain a CSRF token..."
$loginPageResponse = Get-WebResponse -Uri "$AuthServer/login" -CookieJar $cookieJar
$loginPageContent = Read-ResponseBody -Response $loginPageResponse
$loginPageResponse.Close()
$csrfMatch = [regex]::Match($loginPageContent, 'name="_csrf"[^>]*value="([^"]+)"')
if (-not $csrfMatch.Success) { throw "Could not find a CSRF token on the login page." }
$csrfToken = $csrfMatch.Groups[1].Value

Write-Host "Step 2/5: logging in as $Username..."
$loginForm = "username=$([Uri]::EscapeDataString($Username))&password=$([Uri]::EscapeDataString($Password))&_csrf=$([Uri]::EscapeDataString($csrfToken))"
$loginResponse = Get-WebResponse -Uri "$AuthServer/login" -Method "POST" -CookieJar $cookieJar -FormBody $loginForm
$loginLocation = $loginResponse.Headers["Location"]
$loginResponse.Close()
if (-not $loginLocation -or $loginLocation -match "error") {
  throw "Login failed for user '$Username' (redirected to '$loginLocation'). Check the username/password."
}

Write-Host "Step 3/5: requesting an authorization code for $ChatbotClientId (PKCE)..."
$codeVerifier = New-CodeVerifier
$codeChallenge = New-CodeChallenge $codeVerifier
$state = [Guid]::NewGuid().ToString("N")
$authorizeUrl = "$AuthServer/oauth2/authorize" +
  "?response_type=code" +
  "&client_id=$([Uri]::EscapeDataString($ChatbotClientId))" +
  "&scope=$([Uri]::EscapeDataString($Scope))" +
  "&redirect_uri=$([Uri]::EscapeDataString($RedirectUri))" +
  "&code_challenge=$codeChallenge" +
  "&code_challenge_method=S256" +
  "&state=$state"

$authorizeResponse = Get-WebResponse -Uri $authorizeUrl -CookieJar $cookieJar
$location = $authorizeResponse.Headers["Location"]
$authorizeResponse.Close()
if (-not $location) { throw "No redirect received from /oauth2/authorize; login likely failed." }
$codeMatch = [regex]::Match($location, '[?&]code=([^&]+)')
if (-not $codeMatch.Success) {
  throw "No authorization code found in redirect to '$location'. Login likely failed (check username/password)."
}
$authorizationCode = [Uri]::UnescapeDataString($codeMatch.Groups[1].Value)

Write-Host "Step 4/5: exchanging the authorization code for $Username's access token..."
$tokenUri = "$AuthServer/oauth2/token"
$tokenBody = @{
  grant_type    = "authorization_code"
  code          = $authorizationCode
  redirect_uri  = $RedirectUri
  client_id     = $ChatbotClientId
  code_verifier = $codeVerifier
}
$userTokenResponse = Invoke-RestMethod -Uri $tokenUri -Method Post -Body $tokenBody `
  -ContentType "application/x-www-form-urlencoded"
$subjectToken = $userTokenResponse.access_token
if (-not $subjectToken) { throw "Authorization-code exchange did not return an access_token." }
Write-Host "$Username's access token acquired."

Write-Host "Step 5/5: exchanging it (RFC 8693 token-exchange) for an access token for mcp-server..."
$actorTokenResponse = Invoke-RestMethod -Uri $tokenUri -Method Post -ContentType "application/x-www-form-urlencoded" -Body @{
  grant_type    = "client_credentials"
  client_id     = $McpClientId
  client_secret = $McpClientSecret
  scope         = "mcp.tools"
}
$actorToken = $actorTokenResponse.access_token
if (-not $actorToken) { throw "Could not obtain mcp-client's own client-credentials access token." }

$exchangeResponse = Invoke-RestMethod -Uri $tokenUri -Method Post -ContentType "application/x-www-form-urlencoded" -Body @{
  grant_type         = "urn:ietf:params:oauth:grant-type:token-exchange"
  subject_token      = $subjectToken
  subject_token_type = "urn:ietf:params:oauth:token-type:access_token"
  actor_token        = $actorToken
  actor_token_type   = "urn:ietf:params:oauth:token-type:access_token"
  client_id          = $McpClientId
  client_secret      = $McpClientSecret
  scope              = "mcp.tools"
}
$mcpServerToken = $exchangeResponse.access_token
if (-not $mcpServerToken) { throw "Token exchange did not return an access_token." }

Write-Host ""
Write-Host "Access token for mcp-server (sub=$Username, act.sub=$McpClientId):"
Write-Output $mcpServerToken

