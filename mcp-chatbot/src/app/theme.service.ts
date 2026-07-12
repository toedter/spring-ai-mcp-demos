import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark' | 'auto';

/**
 * Manages the application colour theme (light / dark / auto) by setting a
 * `data-theme` attribute on the document root. The CSS variables in
 * styles.css react to this attribute. "auto" follows the operating system
 * preference via the `prefers-color-scheme` media query.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private static readonly STORAGE_KEY = 'mcp-chatbot-theme';
  private readonly darkQuery = window.matchMedia('(prefers-color-scheme: dark)');

  readonly theme = signal<Theme>(this.load());
  /** The effective mode after resolving "auto" against the OS preference. */
  readonly resolvedDark = signal<boolean>(false);

  constructor() {
    this.apply(this.theme());
    this.resolvedDark.set(this.computeDark(this.theme()));

    // Keep "auto" in sync with live OS theme changes.
    this.darkQuery.addEventListener('change', () => {
      if (this.theme() === 'auto') {
        this.resolvedDark.set(this.darkQuery.matches);
      }
    });
  }

  set(theme: Theme): void {
    this.theme.set(theme);
    try {
      localStorage.setItem(ThemeService.STORAGE_KEY, theme);
    } catch {
      // localStorage may be unavailable; ignore.
    }
    this.apply(theme);
    this.resolvedDark.set(this.computeDark(theme));
  }

  private computeDark(theme: Theme): boolean {
    return theme === 'dark' || (theme === 'auto' && this.darkQuery.matches);
  }

  private load(): Theme {
    const stored = (() => {
      try {
        return localStorage.getItem(ThemeService.STORAGE_KEY);
      } catch {
        return null;
      }
    })();
    return stored === 'light' || stored === 'dark' || stored === 'auto' ? stored : 'auto';
  }

  private apply(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
  }
}

