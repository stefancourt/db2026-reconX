// TICKET-ADV112 — withAuth HOC: redirects to /login if no JWT.
import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '@context/AuthContext.jsx';

export function withAuth(Component) {
  function WithAuth(props) {
    const { user } = useAuth();
    if (!user) return <Navigate to="/login" replace />;
    return <Component {...props} />;
  }
  WithAuth.displayName = `withAuth(${Component.displayName || Component.name || 'Component'})`;
  return WithAuth;
}
