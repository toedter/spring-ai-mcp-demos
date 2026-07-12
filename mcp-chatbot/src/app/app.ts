import { Component, CUSTOM_ELEMENTS_SCHEMA, computed, inject } from '@angular/core';
import { AuthService } from './auth.service';
import { MCP_CHAT_URL } from './config';

@Component({
  selector: 'app-root',
  imports: [],
  templateUrl: './app.html',
  styleUrl: './app.css',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class App {
  protected readonly auth = inject(AuthService);

  /** deep-chat connection config, including the bearer token from the login. */
  protected readonly connectConfig = computed(() => ({
    url: MCP_CHAT_URL,
    method: 'POST',
    headers: {
      Authorization: `Bearer ${this.auth.accessToken()}`,
    },
  }));

  protected readonly introMessage = {
    text: 'Hi! I am your MCP assistant. Ask me anything and I will use the available tools when needed.',
  };

  protected readonly textInput = {
    placeholder: { text: 'Type a message...' },
  };

  /** First letters of the user name for the avatar badge. */
  protected readonly initials = computed(() => {
    const name = this.auth.user()?.name?.trim();
    if (!name) {
      return '?';
    }
    const parts = name.split(/\s+/);
    const letters = parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : parts[0][0];
    return letters.toUpperCase();
  });

  login(): void {
    this.auth.login();
  }

  logout(): void {
    this.auth.logout();
  }
}
