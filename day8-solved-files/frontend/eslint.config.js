// ============================================================================
// TICKET-ADV111 — ESLint 9 flat config for React 18 + hooks.
//
// Uses ONLY the plugins already in package.json (eslint-plugin-react +
// eslint-plugin-react-hooks). Globals are declared inline so we don't need
// the `globals` package; recommended JS rules are deliberately omitted so
// we don't need `@eslint/js`. Designed to pass `eslint src --max-warnings 0`
// on the trainer-copy code as it stands.
//
// Note: Vite path aliases (@/…) are resolved at build time, not by ESLint.
// ============================================================================
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';

const browserGlobals = {
  window: 'readonly',
  document: 'readonly',
  navigator: 'readonly',
  console: 'readonly',
  localStorage: 'readonly',
  sessionStorage: 'readonly',
  fetch: 'readonly',
  WebSocket: 'readonly',
  EventSource: 'readonly',
  IntersectionObserver: 'readonly',
  setTimeout: 'readonly',
  clearTimeout: 'readonly',
  setInterval: 'readonly',
  clearInterval: 'readonly',
  URL: 'readonly',
  alert: 'readonly',
  HTMLElement: 'readonly',
  Event: 'readonly',
};

const testGlobals = {
  describe: 'readonly',
  it: 'readonly',
  test: 'readonly',
  expect: 'readonly',
  vi: 'readonly',
  beforeAll: 'readonly',
  beforeEach: 'readonly',
  afterAll: 'readonly',
  afterEach: 'readonly',
};

export default [
  { ignores: ['dist/**', 'node_modules/**', 'coverage/**', '.vite/**'] },

  // App source — JSX, browser runtime.
  {
    files: ['src/**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: 'module',
      parserOptions: { ecmaFeatures: { jsx: true } },
      globals: browserGlobals,
    },
    plugins: { react, 'react-hooks': reactHooks },
    settings: { react: { version: 'detect' } },
    rules: {
      ...react.configs.recommended.rules,
      // Two hooks rules — listed explicitly because eslint-plugin-react-hooks
      // changed how it ships flat configs between minor versions.
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'error',
      // React 17+ JSX transform: no need to import React just to write JSX.
      'react/react-in-jsx-scope': 'off',
      // Trainer copy ships without PropTypes (Day-8 guide discusses choices).
      'react/prop-types': 'off',
      // Tight unused-vars: report as ERROR (so it doesn't burn the warning
      // budget). Skip vars prefixed _ and the React import itself (kept in
      // some files for React.StrictMode / React.Component references).
      'no-unused-vars': ['error', {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^(_|React)',
      }],
      // Production code should not leak console.* — the one place that needs
      // it (withErrorBoundary.jsx) carries an explicit eslint-disable-next-line.
      'no-console': 'error',
    },
  },

  // Tests — Vitest globals + slightly more permissive (still browser env).
  {
    files: ['src/**/*.test.{js,jsx}', 'src/**/__tests__/**/*.{js,jsx}', 'src/test-setup.js'],
    languageOptions: {
      globals: { ...browserGlobals, ...testGlobals },
    },
  },
];
