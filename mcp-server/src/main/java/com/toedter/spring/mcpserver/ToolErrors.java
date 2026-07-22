/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.toedter.spring.mcpserver;

import org.slf4j.Logger;

/**
 * Turns a caught exception into a sanitized error safe to return to an MCP client (and,
 * transitively, the model). The real exception — which may contain upstream URLs, response bodies,
 * or other internal details — is logged server-side; only a generic, non-sensitive message is
 * propagated.
 */
final class ToolErrors {

  private ToolErrors() {}

  /**
   * Logs {@code cause} at {@code WARN} and returns an {@link IllegalStateException} carrying only
   * {@code publicMessage}, suitable for the caller to throw so it becomes the tool's error content.
   */
  static IllegalStateException sanitized(Logger log, String publicMessage, Exception cause) {
    log.warn(publicMessage, cause);
    return new IllegalStateException(publicMessage);
  }
}
