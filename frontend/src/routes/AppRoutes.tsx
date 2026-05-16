import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { LoginPage } from '../pages/auth/LoginPage';
import { CatalogPage } from '../pages/client/CatalogPage';
import { WorkshopDetailPage } from '../pages/client/WorkshopDetailPage';
import { ProfileSettingsPage } from '../pages/client/ProfileSettingsPage';
import { MyTicketsPage } from '../pages/client/MyTicketsPage';
import { DashboardPage } from '../pages/organizer/DashboardPage';
import { MyOrganizationsPage } from '../pages/organizer/MyOrganizationsPage';
import { OrganizationDetailsPage } from '../pages/organizer/OrganizationDetailsPage';
import { EditOrganizationPage } from '../pages/organizer/EditOrganizationPage';
import { OrganizerProfileSettingsPage } from '../pages/organizer/OrganizerProfileSettingsPage';
import { useAuthStore } from '../hooks/useAuthStore';

function ProtectedRoute({ children, role }: { children: React.ReactNode, role?: 'STUDENT' | 'ORGANIZER' | 'STAFF' }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const user = useAuthStore((s) => s.user);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (role && user?.role !== role) {
    // If user is logged in but doesn't have the right role, redirect to their default home
    return <Navigate to={user?.role === 'ORGANIZER' ? '/organizer' : '/'} replace />;
  }

  return <>{children}</>;
}

export const AppRoutes: React.FC = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        
        {/* Student Routes */}
        <Route path="/" element={<ProtectedRoute role="STUDENT"><CatalogPage /></ProtectedRoute>} />
        <Route path="/workshops/:id" element={<ProtectedRoute role="STUDENT"><WorkshopDetailPage /></ProtectedRoute>} />
        <Route path="/profile" element={<ProtectedRoute><ProfileSettingsPage /></ProtectedRoute>} />
        <Route path="/my-tickets" element={<ProtectedRoute role="STUDENT"><MyTicketsPage /></ProtectedRoute>} />
        
        {/* Organizer Routes */}
        <Route path="/organizer" element={<ProtectedRoute role="ORGANIZER"><DashboardPage /></ProtectedRoute>} />
        <Route path="/organizer/organizations" element={<ProtectedRoute role="ORGANIZER"><MyOrganizationsPage /></ProtectedRoute>} />
        <Route path="/organizer/organizations/:id" element={<ProtectedRoute role="ORGANIZER"><OrganizationDetailsPage /></ProtectedRoute>} />
        <Route path="/organizer/organizations/:id/edit" element={<ProtectedRoute role="ORGANIZER"><EditOrganizationPage /></ProtectedRoute>} />
        <Route path="/organizer/settings" element={<ProtectedRoute role="ORGANIZER"><OrganizerProfileSettingsPage /></ProtectedRoute>} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
};
