// AuthContext used by withAuth HOC; JWT persisted in sessionStorage.
import React, { createContext, useContext, useState } from 'react';

export const AuthContext = createContext({ user: null, login: () => {}, logout: () => {} });

function readInitialUser() {
  if (typeof sessionStorage === 'undefined') return null;
  const token = sessionStorage.getItem('reconx-token');
  const role  = sessionStorage.getItem('reconx-role');
  return token ? { token, role } : null;
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readInitialUser);

  const login = (token, role) => {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.setItem('reconx-token', token);
      if (role) sessionStorage.setItem('reconx-role', role);
    }
    setUser({ token, role });
  };

  const logout = () => {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.removeItem('reconx-token');
      sessionStorage.removeItem('reconx-role');
    }
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
