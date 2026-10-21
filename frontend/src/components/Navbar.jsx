import React, { useContext } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import NotificationCenter from './NotificationCenter';

const Navbar = () => {
    const { isAuthenticated, user, logout } = useContext(AuthContext);
    const navigate = useNavigate();

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    const role = user?.role || 'GUEST';

    return (
        <nav style={{
            backgroundColor: '#0f172a', // Deep Navy
            color: '#ffffff',
            padding: '12px 24px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
            position: 'sticky',
            top: 0,
            zIndex: 900
        }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
                <Link to="/" style={{ textDecoration: 'none', color: '#ffffff', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '20px' }}>🏨</span>
                    <div>
                        <span style={{ fontWeight: 700, fontSize: '16px', letterSpacing: '0.5px' }}>SSN GRAND HOTEL</span>
                        <span style={{ display: 'block', fontSize: '10px', color: '#94a3b8' }}>UCS3513 Distributed Prototype</span>
                    </div>
                </Link>

                {isAuthenticated && (
                    <div style={{ display: 'flex', gap: '16px', marginLeft: '12px' }}>
                        <Link to="/search" style={{ color: '#e2e8f0', textDecoration: 'none', fontSize: '14px', fontWeight: 500 }}>
                            Search & Book
                        </Link>
                        <Link to="/my-bookings" style={{ color: '#e2e8f0', textDecoration: 'none', fontSize: '14px', fontWeight: 500 }}>
                            My Bookings
                        </Link>

                        {(role === 'RECEPTIONIST' || role === 'ADMIN') && (
                            <Link to="/reception" style={{ color: '#38bdf8', textDecoration: 'none', fontSize: '14px', fontWeight: 500 }}>
                                📋 Front Desk
                            </Link>
                        )}

                        {(role === 'HOUSEKEEPING' || role === 'ADMIN') && (
                            <Link to="/housekeeping" style={{ color: '#fbbf24', textDecoration: 'none', fontSize: '14px', fontWeight: 500 }}>
                                🧹 Housekeeping
                            </Link>
                        )}

                        <Link to="/system-design" style={{ color: '#2dd4bf', textDecoration: 'none', fontSize: '14px', fontWeight: 600 }}>
                            ⚡ System Design & Benchmarks
                        </Link>
                    </div>
                )}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                {isAuthenticated ? (
                    <>
                        <NotificationCenter />
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <span className={`badge badge-role-${role.toLowerCase()}`}>
                                {role}
                            </span>
                            <span style={{ fontSize: '13px', color: '#cbd5e1' }}>
                                {user?.fullName || user?.email}
                            </span>
                        </div>
                        <button 
                            onClick={handleLogout}
                            style={{
                                backgroundColor: '#334155',
                                color: '#f8fafc',
                                padding: '6px 12px',
                                borderRadius: '6px',
                                fontSize: '12px'
                            }}
                        >
                            Logout
                        </button>
                    </>
                ) : (
                    <div style={{ display: 'flex', gap: '8px' }}>
                        <Link to="/login">
                            <button className="btn-primary" style={{ padding: '6px 14px', fontSize: '13px' }}>
                                Login
                            </button>
                        </Link>
                        <Link to="/register">
                            <button className="btn-secondary" style={{ padding: '6px 14px', fontSize: '13px' }}>
                                Register
                            </button>
                        </Link>
                    </div>
                )}
            </div>
        </nav>
    );
};

export default Navbar;
