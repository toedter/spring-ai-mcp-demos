import {
  Component,
  CUSTOM_ELEMENTS_SCHEMA,
  HostListener,
  computed,
  inject,
  signal,
} from '@angular/core';
import { AuthService } from './auth.service';
import { ThemeService, Theme } from './theme.service';
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
  protected readonly theme = inject(ThemeService);

  /** Whether the user dropdown menu is open. */
  protected readonly menuOpen = signal(false);
  /** Whether the theme dropdown menu is open. */
  protected readonly themeMenuOpen = signal(false);

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

  // ----- Theme-aware deep-chat styling -----

  /** Host element style: sizes the widget AND sets its background/text colour. */
  protected readonly chatStyle = computed(() => {
    const dark = this.theme.resolvedDark();
    const bg = dark ? '#24262e' : '#ffffff';
    const text = dark ? '#e8eaed' : '#1f2430';
    return `width:100%;height:100%;border:none;border-radius:0;background-color:${bg};color:${text};`;
  });

  /** Message bubble colours. */
  protected readonly messageStyles = computed(() => {
    const dark = this.theme.resolvedDark();
    return {
      default: {
        shared: { bubble: { color: dark ? '#e8eaed' : '#1f2430' } },
        ai: { bubble: { backgroundColor: dark ? '#2f323c' : '#f1f3f8' } },
        user: { bubble: { backgroundColor: '#5b78e6', color: '#ffffff' } },
      },
    };
  });

  /** Text input area colours + placeholder. */
  protected readonly textInput = computed(() => {
    const dark = this.theme.resolvedDark();
    return {
      styles: {
        container: {
          backgroundColor: dark ? '#2f323c' : '#ffffff',
          border: dark ? '1px solid #3a3d46' : '1px solid #e0e0e0',
          color: dark ? '#e8eaed' : '#1f2430',
        },
        text: { color: dark ? '#e8eaed' : '#1f2430' },
      },
      placeholder: {
        text: 'Type a message...',
        style: { color: dark ? '#9aa0aa' : '#6b7280' },
      },
    };
  });

  /** Submit button colour. */
  protected readonly submitButtonStyles = computed(() => ({
    submit: {
      container: { default: { backgroundColor: '#5b78e6' } },
      svg: { styles: { default: { filter: 'brightness(0) invert(1)' } } },
    },
  }));

  /** Scrollbar styling injected into the deep-chat shadow DOM. */
  protected readonly auxiliaryStyle = computed(() => {
    const dark = this.theme.resolvedDark();
    return `
      ::-webkit-scrollbar { width: 8px; }
      ::-webkit-scrollbar-track { background: ${dark ? '#24262e' : '#ffffff'}; }
      ::-webkit-scrollbar-thumb { background: ${dark ? '#3a3d46' : '#c9ced8'}; border-radius: 4px; }
    `;
  });

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
    this.themeMenuOpen.set(false);
    this.menuOpen.update((open) => !open);
  }

  toggleThemeMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.menuOpen.set(false);
    this.themeMenuOpen.update((open) => !open);
  }

  setTheme(theme: Theme): void {
    this.theme.set(theme);
    this.themeMenuOpen.set(false);
  }

  /** Close the menus when clicking anywhere outside of them. */
  @HostListener('document:click')
  closeMenus(): void {
    this.menuOpen.set(false);
    this.themeMenuOpen.set(false);
  }

  login(): void {
    this.auth.login();
  }

  logout(): void {
    this.menuOpen.set(false);
    this.auth.logout();
  }
}
