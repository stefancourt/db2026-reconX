// TICKET-ADV124 — ThemeProvider: context flips data-theme; CSS owns colours.
import React, { createContext, useContext, useState } from 'react';

const ThemeContext = createContext({ theme: 'light', toggle: () => {} });

export function ThemeProvider({ children }) {
  // TODO(TICKET-ADV124): lazy-init from localStorage('reconx-theme') — fall back
  //                     to 'light' if nothing is stored.
  const [theme /*, setTheme */] = useState('light');

  // TODO(TICKET-ADV124): useEffect that:
  //                     1. sets document.documentElement.dataset.theme = theme
  //                     2. persists `theme` to localStorage on every change.

  const toggle = () => {
    // TODO(TICKET-ADV124): flip 'light' <-> 'dark' via setTheme(prev => ...).
  };

  return (
    <ThemeContext.Provider value={{ theme, toggle }}>
      {children}
    </ThemeContext.Provider>
  );
}

export const useTheme = () => useContext(ThemeContext);
