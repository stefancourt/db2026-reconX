// withAuth HOC: redirects to /login if no JWT.
import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@context/AuthContext.jsx';

export function withAuth(Component) {
  function WithAuth(props) {
    const { user } = useAuth();
    const location = useLocation();
    if (!user) {
      return <Navigate to="/login" replace state={{ from: location.pathname }} />;
    }
    return <Component {...props} />;
  }
  WithAuth.displayName = `withAuth(${Component.displayName || Component.name || 'Component'})`;
  return WithAuth;
}
