// TICKET-ADV112 — withAuth HOC: redirects to /login if no JWT.
import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '@context/AuthContext.jsx';

export function withAuth(Component) {
  function WithAuth(props) {
    // TODO(TICKET-ADV112): read `user` from useAuth(); if falsy, return
    //                     <Navigate to="/login" replace />, otherwise render
    //                     the wrapped <Component {...props} />.
    return <Component {...props} />;
  }
  WithAuth.displayName = `withAuth(${Component.displayName || Component.name || 'Component'})`;
  return WithAuth;
}
