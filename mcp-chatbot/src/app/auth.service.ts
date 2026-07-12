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
    try {
      await this.oauth.loadDiscoveryDocumentAndTryLogin();
    } catch (error) {
      console.error('OIDC discovery / login failed', error);
    }
    this.refresh();
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

  private refresh(): void {
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

