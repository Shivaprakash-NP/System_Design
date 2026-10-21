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

const ProtectedRoute = ({ children, allowedRoles }) => {
    const { isAuthenticated, user } = useContext(AuthContext);

    if (!isAuthenticated) {
        return <Navigate to="/login" />;
    }

    if (allowedRoles && (!user || !allowedRoles.includes(user.role))) {
        return <Navigate to="/search" />;
    }

    return children;
};

function App() {
    return (
        <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
            <Navbar />
            <main style={{ flex: 1, padding: '20px 12px' }}>
                <Routes>
                    <Route path="/login" element={<Login />} />
                    <Route path="/register" element={<Register />} />
                    
                    <Route path="/search" element={<RoomSearch />} />
                    <Route path="/book" element={<RoomSearch />} />
                    
                    <Route path="/my-bookings" element={
                        <ProtectedRoute>
                            <GuestDashboard />
                        </ProtectedRoute>
                    } />

                    <Route path="/reception" element={
                        <ProtectedRoute allowedRoles={['RECEPTIONIST', 'ADMIN']}>
                            <ReceptionDashboard />
                        </ProtectedRoute>
                    } />

                    <Route path="/housekeeping" element={
                        <ProtectedRoute allowedRoles={['HOUSEKEEPING', 'ADMIN']}>
                            <HousekeepingDashboard />
                        </ProtectedRoute>
                    } />

                    <Route path="/invoice/:invoiceId" element={<InvoiceView />} />
                    
                    <Route path="/system-design" element={<SystemDesignDashboard />} />

                    <Route path="/" element={<Navigate to="/search" />} />
                    <Route path="*" element={<Navigate to="/search" />} />
                </Routes>
            </main>
        </div>
    );
}

export default App;
