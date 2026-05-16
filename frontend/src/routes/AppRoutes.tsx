import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { LoginPage } from '../pages/auth/LoginPage';
import { CatalogPage } from '../pages/client/CatalogPage';
import { WorkshopDetailPage } from '../pages/client/WorkshopDetailPage';
import { ProfileSettingsPage } from '../pages/client/ProfileSettingsPage';
import { MyTicketsPage } from '../pages/client/MyTicketsPage';
import { useAuthStore } from '../hooks/useAuthStore';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />;
}

export const AppRoutes: React.FC = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<ProtectedRoute><CatalogPage /></ProtectedRoute>} />
        <Route path="/workshops/:id" element={<ProtectedRoute><WorkshopDetailPage /></ProtectedRoute>} />
        <Route path="/profile" element={<ProtectedRoute><ProfileSettingsPage /></ProtectedRoute>} />
        <Route path="/my-tickets" element={<ProtectedRoute><MyTicketsPage /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
};
