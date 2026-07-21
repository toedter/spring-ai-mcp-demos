import { Injectable, inject, signal } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { authConfig } from './config';

export interface UserProfile {
  name: string;
  email: string;
}

/**
 * Wraps angular-oauth2-oidc to run the Authorization Code + PKCE flow against
 * the mcp-authorization-server and expose the logged-in user as signals.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);

  readonly user = signal<UserProfile | null>(null);
  readonly accessToken = signal<string>('');
  readonly loggedIn = signal<boolean>(false);

  /** Called once on application startup (see app.config.ts). */
  async init(): Promise<void> {
    this.oauth.configure(authConfig);

    // Keep the signals and the access-token expiry timer in sync with every
    // token issued, whether from login or a background refresh.
    this.oauth.events.subscribe((event) => {
      switch (event.type) {
        case 'token_received':
        case 'token_refreshed':
          this.syncFromOAuthService();
          break;
        case 'token_refresh_error':
          // The refresh token itself is no longer usable (expired/revoked);
          // silent recovery isn't possible, so drop back to the login screen.
          console.error('Token refresh failed, logging out', event);
          this.logout();
          break;
      }
    });

    try {
      await this.oauth.loadDiscoveryDocumentAndTryLogin();
      // Automatically renews the access token shortly before it expires,
      // using the refresh_token grant (responseType is 'code', so this calls
      // oauth.refreshToken() internally rather than an iframe-based flow).
      this.oauth.setupAutomaticSilentRefresh();
    } catch (error) {
      console.error('OIDC discovery / login failed', error);
    }
    this.syncFromOAuthService();
  }

  login(): void {
    this.oauth.initCodeFlow();
  }

  logout(): void {
    this.oauth.logOut();
    this.user.set(null);
    this.accessToken.set('');
    this.loggedIn.set(false);
  }

  /** Forces an immediate access-token refresh, e.g. after a request comes back 401. */
  async refreshAccessToken(): Promise<string> {
    await this.oauth.refreshToken();
    return this.oauth.getAccessToken();
  }

  private syncFromOAuthService(): void {
    const valid = this.oauth.hasValidAccessToken();
    this.loggedIn.set(valid);
    this.accessToken.set(valid ? this.oauth.getAccessToken() : '');

    const claims = this.oauth.getIdentityClaims() as Record<string, unknown> | null;
    if (valid && claims) {
      this.user.set({
        name: (claims['name'] as string) ?? (claims['sub'] as string) ?? 'Unknown user',
        email: (claims['email'] as string) ?? (claims['sub'] as string) ?? '',
      });
    } else {
      this.user.set(null);
    }
  }
}

