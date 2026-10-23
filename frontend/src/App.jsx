import React, { useContext } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthContext } from './context/AuthContext';
import Login from './pages/Login';
import Register from './pages/Register';
import RoomSearch from './pages/RoomSearch';
import GuestDashboard from './pages/GuestDashboard';
import ReceptionDashboard from './pages/ReceptionDashboard';
import HousekeepingDashboard from './pages/HousekeepingDashboard';
import InvoiceView from './pages/InvoiceView';
import SystemDesignDashboard from './pages/SystemDesignDashboard';
import Navbar from './components/Navbar';

// Strict Role Guard for Protected Routes
const ProtectedRoute = ({ children, allowedRoles }) => {
    const { isAuthenticated, user } = useContext(AuthContext);

    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    if (allowedRoles && (!user || !allowedRoles.includes(user.role))) {
        // Redirect user to their designated home module
        if (user?.role === 'RECEPTIONIST') return <Navigate to="/reception" replace />;
        if (user?.role === 'HOUSEKEEPING') return <Navigate to="/housekeeping" replace />;
        return <Navigate to="/search" replace />;
    }

    return children;
};

// Guard for Guest-Only Pages: ensures Receptionists and Housekeeping staff stay in their dedicated portals
const GuestOrAdminRoute = ({ children }) => {
    const { isAuthenticated, user } = useContext(AuthContext);

    if (isAuthenticated) {
        if (user?.role === 'RECEPTIONIST') return <Navigate to="/reception" replace />;
        if (user?.role === 'HOUSEKEEPING') return <Navigate to="/housekeeping" replace />;
    }

    return children;
};

// Root route resolver based on authenticated role
const DefaultRedirect = () => {
    const { isAuthenticated, user } = useContext(AuthContext);

    if (!isAuthenticated) return <Navigate to="/search" replace />;
    if (user?.role === 'RECEPTIONIST') return <Navigate to="/reception" replace />;
    if (user?.role === 'HOUSEKEEPING') return <Navigate to="/housekeeping" replace />;
    return <Navigate to="/search" replace />;
};

function App() {
    return (
        <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', backgroundColor: 'var(--bg-primary, #FAF9F6)' }}>
            <Navbar />
            <main style={{ flex: 1, padding: '24px 16px' }}>
                <Routes>
                    {/* Public Auth Routes */}
                    <Route path="/login" element={<Login />} />
                    <Route path="/register" element={<Register />} />
                    
                    {/* Guest Booking Discovery (Guarded against Front Desk & Housekeeping staff) */}
                    <Route path="/search" element={
                        <GuestOrAdminRoute>
                            <RoomSearch />
                        </GuestOrAdminRoute>
                    } />
                    <Route path="/book" element={
                        <GuestOrAdminRoute>
                            <RoomSearch />
                        </GuestOrAdminRoute>
                    } />
                    
                    {/* Guest Reservations Portal */}
                    <Route path="/my-bookings" element={
                        <ProtectedRoute allowedRoles={['GUEST', 'ADMIN']}>
                            <GuestDashboard />
                        </ProtectedRoute>
                    } />

                    {/* Front Desk Receptionist Portal */}
                    <Route path="/reception" element={
                        <ProtectedRoute allowedRoles={['RECEPTIONIST', 'ADMIN']}>
                            <ReceptionDashboard />
                        </ProtectedRoute>
                    } />

                    {/* Housekeeping Operational Portal */}
                    <Route path="/housekeeping" element={
                        <ProtectedRoute allowedRoles={['HOUSEKEEPING', 'ADMIN']}>
                            <HousekeepingDashboard />
                        </ProtectedRoute>
                    } />

                    {/* Stay Invoice View */}
                    <Route path="/invoice/:invoiceId" element={<InvoiceView />} />
                    
                    {/* System Design & Benchmarks: VISIBLE & ACCESSIBLE TO ALL FOUR ROLES */}
                    <Route path="/system-design" element={<SystemDesignDashboard />} />

                    {/* Role-Aware Fallbacks */}
                    <Route path="/" element={<DefaultRedirect />} />
                    <Route path="*" element={<DefaultRedirect />} />
                </Routes>
            </main>
        </div>
    );
}

export default App;
