import React, { useState, useContext } from 'react';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';
import { useNavigate, Link } from 'react-router-dom';

const Login = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const { login } = useContext(AuthContext);
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const response = await api.post('/auth/login', { email, password });
            login(response.data);
            const role = response.data?.role;
            if (role === 'RECEPTIONIST') {
                navigate('/reception');
            } else if (role === 'HOUSEKEEPING') {
                navigate('/housekeeping');
            } else {
                navigate('/search');
            }
        } catch (err) {
            setError(err.response?.data?.error || 'Login failed. Please check credentials.');
        } finally {
            setLoading(false);
        }
    };

    const fillDemo = (demoEmail, demoPass) => {
        setEmail(demoEmail);
        setPassword(demoPass);
        setError('');
    };

    return (
        <div style={{ maxWidth: '440px', margin: '48px auto', padding: '36px 32px', backgroundColor: '#ffffff', borderRadius: '16px', boxShadow: 'var(--shadow-md)', border: '1px solid var(--border-color, #E5E7E1)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px', justifyContent: 'center' }}>
                <div style={{
                    backgroundColor: 'var(--primary-brand, #174A3A)',
                    color: 'var(--accent-gold, #C5A46D)',
                    width: '32px',
                    height: '32px',
                    borderRadius: '8px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 900,
                    fontSize: '18px',
                    fontFamily: 'Playfair Display, serif'
                }}>
                    B.
                </div>
                <span style={{ fontFamily: 'Playfair Display, serif', fontSize: '22px', fontWeight: 700, color: 'var(--text-primary, #252925)', letterSpacing: '-0.02em' }}>
                    Booking.com
                </span>
            </div>

            <h2 style={{ fontFamily: 'Playfair Display, serif', fontSize: '22px', fontWeight: 700, color: 'var(--text-primary, #252925)', marginBottom: '6px', textAlign: 'center' }}>
                Sign In
            </h2>
            <p style={{ fontSize: '13px', color: 'var(--text-secondary, #70766F)', marginBottom: '24px', textAlign: 'center' }}>
                Access your guest reservations or staff operational console
            </p>

            {error && (
                <div style={{ backgroundColor: '#fee2e2', color: '#b91c1c', padding: '10px 14px', borderRadius: '8px', fontSize: '13px', marginBottom: '18px', border: '1px solid #fecaca' }}>
                    {error}
                </div>
            )}

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, color: 'var(--text-primary, #252925)', marginBottom: '6px', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                        Email Address
                    </label>
                    <input 
                        type="email" 
                        placeholder="e.g. guest@hotel.demo" 
                        value={email} 
                        onChange={(e) => setEmail(e.target.value)} 
                        required 
                        style={{ 
                            width: '100%',
                            padding: '11px 14px',
                            fontSize: '13px',
                            borderRadius: '8px',
                            border: '1px solid var(--border-color, #E5E7E1)',
                            backgroundColor: '#FFFFFF',
                            color: 'var(--text-primary, #252925)'
                        }}
                    />
                </div>

                <div>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, color: 'var(--text-primary, #252925)', marginBottom: '6px', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                        Password
                    </label>
                    <input 
                        type="password" 
                        placeholder="••••••••" 
                        value={password} 
                        onChange={(e) => setPassword(e.target.value)} 
                        required 
                        style={{ 
                            width: '100%',
                            padding: '11px 14px',
                            fontSize: '13px',
                            borderRadius: '8px',
                            border: '1px solid var(--border-color, #E5E7E1)',
                            backgroundColor: '#FFFFFF',
                            color: 'var(--text-primary, #252925)'
                        }}
                    />
                </div>

                <button 
                    type="submit" 
                    className="btn-primary" 
                    disabled={loading}
                    style={{ padding: '12px', fontSize: '14px', marginTop: '6px' }}
                >
                    {loading ? 'Authenticating...' : 'Sign In'}
                </button>
            </form>

            <div style={{ marginTop: '28px', borderTop: '1px solid var(--border-color, #E5E7E1)', paddingTop: '20px' }}>
                <p style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-secondary, #70766F)', marginBottom: '10px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                    Quick Fill Demo Accounts:
                </p>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                    <button 
                        type="button" 
                        onClick={() => fillDemo('guest@hotel.demo', 'Guest@12345')}
                        className="btn-secondary"
                        style={{ fontSize: '12px', padding: '8px 10px', textAlign: 'left', borderRadius: '8px' }}
                    >
                        👤 Guest Demo
                    </button>
                    <button 
                        type="button" 
                        onClick={() => fillDemo('reception@hotel.demo', 'Reception@12345')}
                        className="btn-secondary"
                        style={{ fontSize: '12px', padding: '8px 10px', textAlign: 'left', borderRadius: '8px' }}
                    >
                        📋 Receptionist
                    </button>
                    <button 
                        type="button" 
                        onClick={() => fillDemo('housekeeping@hotel.demo', 'Housekeep@12345')}
                        className="btn-secondary"
                        style={{ fontSize: '12px', padding: '8px 10px', textAlign: 'left', borderRadius: '8px' }}
                    >
                        🧹 Housekeeping
                    </button>
                    <button 
                        type="button" 
                        onClick={() => fillDemo('admin@hotel.demo', 'Admin@12345')}
                        className="btn-secondary"
                        style={{ fontSize: '12px', padding: '8px 10px', textAlign: 'left', borderRadius: '8px' }}
                    >
                        🛡️ Admin Demo
                    </button>
                </div>
            </div>

            <p style={{ marginTop: '24px', fontSize: '13px', color: 'var(--text-secondary, #70766F)', textAlign: 'center' }}>
                Don't have an account? <Link to="/register" style={{ color: 'var(--primary-brand, #174A3A)', fontWeight: 700, textDecoration: 'none' }}>Register here</Link>
            </p>
        </div>
    );
};

export default Login;
