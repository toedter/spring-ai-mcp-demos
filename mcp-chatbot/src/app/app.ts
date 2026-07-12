import {
  Component,
  CUSTOM_ELEMENTS_SCHEMA,
  HostListener,
  computed,
  inject,
  signal,
} from '@angular/core';
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

  /** Whether the user dropdown menu is open. */
  protected readonly menuOpen = signal(false);

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

  /** First letters derived from the user name (or email as a fallback). */
  protected readonly initials = computed(() => {
    const user = this.auth.user();
    const source = user?.name?.trim() || user?.email?.trim();
    if (!source) {
      return '?';
    }
    const parts = source.split(/[\s@._-]+/).filter(Boolean);
    const letters =
      parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : parts[0][0];
    return letters.toUpperCase();
  });

  toggleMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.menuOpen.update((open) => !open);
  }

  /** Close the menu when clicking anywhere outside of it. */
  @HostListener('document:click')
  closeMenu(): void {
    this.menuOpen.set(false);
  }

  login(): void {
    this.auth.login();
  }

  logout(): void {
    this.menuOpen.set(false);
    this.auth.logout();
  }
}
