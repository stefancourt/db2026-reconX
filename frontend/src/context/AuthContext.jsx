// TICKET-ADV112 — AuthContext used by withAuth HOC; JWT persisted in memory
// (refresh path lives in HttpOnly cookie — out of scope for this trainer copy).
import React, { createContext, useContext, useState } from 'react';

const AuthContext = createContext({ user: null, login: () => {}, logout: () => {} });

export function AuthProvider({ children }) {
  // TODO(TICKET-ADV112): lazy-init `user` from sessionStorage so a page
  //                     refresh doesn't blow the JWT away. Look for keys
  //                     'reconx-token' and 'reconx-role'.
  const [user /*, setUser */] = useState(null);

  const login = (/* token, role */) => {
    // TODO(TICKET-ADV112): persist token+role to sessionStorage and call setUser.
  };

  const logout = () => {
    // TODO(TICKET-ADV112): clear sessionStorage and reset user state to null.
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
