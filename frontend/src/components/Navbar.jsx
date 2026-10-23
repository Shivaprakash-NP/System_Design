import React, { useContext } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import NotificationCenter from './NotificationCenter';

const Navbar = () => {
    const { isAuthenticated, user, logout } = useContext(AuthContext);
    const navigate = useNavigate();
    const location = useLocation();

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    const role = user?.role || 'GUEST';

    const getHomePath = () => {
        if (!isAuthenticated) return '/login';
        if (role === 'RECEPTIONIST') return '/reception';
        if (role === 'HOUSEKEEPING') return '/housekeeping';
        return '/search';
    };

    const isActive = (path) => location.pathname === path;

    const navLinkStyle = (path, accentColor = null) => {
        const active = isActive(path);
        return {
            color: active 
                ? (accentColor || '#FFFFFF') 
                : 'rgba(255, 255, 255, 0.78)',
            textDecoration: 'none',
            fontSize: '13.5px',
            fontWeight: active ? 600 : 500,
            padding: '6px 12px',
            borderRadius: '6px',
            backgroundColor: active ? 'rgba(255, 255, 255, 0.12)' : 'transparent',
            border: active && accentColor ? `1px solid ${accentColor}40` : '1px solid transparent',
            transition: 'all 0.15s ease',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px'
        };
    };

    return (
        <nav style={{
            backgroundColor: '#174A3A',
            color: '#FFFFFF',
            padding: '12px 28px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            boxShadow: '0 4px 20px rgba(16, 56, 44, 0.15)',
            borderBottom: '1px solid rgba(197, 164, 109, 0.3)',
            position: 'sticky',
            top: 0,
            zIndex: 900,
            flexWrap: 'wrap',
            gap: '12px'
        }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '28px', flexWrap: 'wrap' }}>
                {/* Brand Logo */}
                <Link to={getHomePath()} style={{ textDecoration: 'none', color: '#FFFFFF', display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <div style={{
                        backgroundColor: '#C5A46D',
                        color: '#174A3A',
                        width: '34px',
                        height: '34px',
                        borderRadius: '6px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontWeight: 900,
                        fontSize: '18px',
                        letterSpacing: '-0.5px',
                        boxShadow: '0 2px 6px rgba(0,0,0,0.15)'
                    }}>
                        B.
                    </div>
                    <div>
                        <span style={{ fontWeight: 800, fontSize: '18px', letterSpacing: '-0.02em', color: '#FFFFFF' }}>
                            Booking<span style={{ color: '#C5A46D' }}>.com</span>
                        </span>
                        <span style={{ display: 'block', fontSize: '10px', color: '#C5A46D', fontWeight: 500, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
                            Luxury Hospitality Platform
                        </span>
                    </div>
                </Link>

                {/* Role-Specific Navigation Links */}
                <div style={{ display: 'flex', gap: '6px', alignItems: 'center', flexWrap: 'wrap' }}>
                    {!isAuthenticated ? (
                        <>
                            <Link to="/search" style={navLinkStyle('/search')}>
                                🔍 Search & Book
                            </Link>
                            <Link to="/system-design" style={navLinkStyle('/system-design', '#C5A46D')}>
                                ⚡ System Design & Benchmarks
                            </Link>
                        </>
                    ) : (
                        <>
                            {/* GUEST & ADMIN only see booking discovery pages */}
                            {(role === 'GUEST' || role === 'ADMIN') && (
                                <>
                                    <Link to="/search" style={navLinkStyle('/search')}>
                                        🔍 Search & Book
                                    </Link>
                                    <Link to="/my-bookings" style={navLinkStyle('/my-bookings')}>
                                        🧳 My Bookings
                                    </Link>
                                </>
                            )}

                            {/* RECEPTIONIST & ADMIN only see Front Desk */}
                            {(role === 'RECEPTIONIST' || role === 'ADMIN') && (
                                <Link to="/reception" style={navLinkStyle('/reception', '#C5A46D')}>
                                    📋 Front Desk
                                </Link>
                            )}

                            {/* HOUSEKEEPING & ADMIN only see Housekeeping */}
                            {(role === 'HOUSEKEEPING' || role === 'ADMIN') && (
                                <Link to="/housekeeping" style={navLinkStyle('/housekeeping', '#C5A46D')}>
                                    🧹 Housekeeping
                                </Link>
                            )}

                            {/* SYSTEM DESIGN & BENCHMARKS IS ACCESSIBLE TO ALL FOUR ROLES */}
                            <Link to="/system-design" style={navLinkStyle('/system-design', '#C5A46D')}>
                                ⚡ System Design & Benchmarks
                            </Link>
                        </>
                    )}
                </div>
            </div>

            {/* User Profile & Actions */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px', flexWrap: 'wrap' }}>
                {isAuthenticated ? (
                    <>
                        <NotificationCenter />
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <span style={{
                                backgroundColor: 'rgba(197, 164, 109, 0.2)',
                                color: '#C5A46D',
                                border: '1px solid rgba(197, 164, 109, 0.4)',
                                padding: '2px 8px',
                                borderRadius: '4px',
                                fontSize: '10.5px',
                                fontWeight: 700,
                                letterSpacing: '0.04em'
                            }}>
                                {role}
                            </span>
                            <span style={{ fontSize: '13px', color: '#F0F1EC', fontWeight: 500 }}>
                                {user?.fullName || user?.email}
                            </span>
                        </div>
                        <button 
                            onClick={handleLogout}
                            style={{
                                backgroundColor: 'rgba(255, 255, 255, 0.1)',
                                color: '#FFFFFF',
                                padding: '6px 14px',
                                borderRadius: '6px',
                                fontSize: '12px',
                                border: '1px solid rgba(255, 255, 255, 0.2)',
                                transition: 'all 0.15s ease'
                            }}
                            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.2)'}
                            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.1)'}
                        >
                            Sign Out
                        </button>
                    </>
                ) : (
                    <div style={{ display: 'flex', gap: '8px' }}>
                        <Link to="/login">
                            <button className="btn-secondary" style={{ padding: '6px 14px', fontSize: '12.5px', backgroundColor: 'transparent', color: '#FFFFFF', borderColor: 'rgba(255, 255, 255, 0.3)' }}>
                                Sign In
                            </button>
                        </Link>
                        <Link to="/register">
                            <button className="btn-gold" style={{ padding: '6px 14px', fontSize: '12.5px' }}>
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