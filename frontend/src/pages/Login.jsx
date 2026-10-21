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
            navigate('/search');
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
        <div style={{ maxWidth: '440px', margin: '40px auto', padding: '28px', backgroundColor: '#ffffff', borderRadius: '12px', boxShadow: '0 4px 20px rgba(0,0,0,0.06)', border: '1px solid #e2e8f0' }}>
            <h2 style={{ fontSize: '22px', fontWeight: 700, color: '#0f172a', marginBottom: '8px' }}>Sign In</h2>
            <p style={{ fontSize: '13px', color: '#64748b', marginBottom: '20px' }}>Access your hotel reservation and staff management dashboard.</p>

            {error && (
                <div style={{ backgroundColor: '#fee2e2', color: '#b91c1c', padding: '10px 14px', borderRadius: '6px', fontSize: '13px', marginBottom: '16px' }}>
                    {error}
                </div>
            )}

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div>
                    <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, color: '#334155', marginBottom: '4px' }}>Email Address</label>
                    <input 
                        type="email" 
                        placeholder="e.g. guest@hotel.demo" 
                        value={email} 
                        onChange={(e) => setEmail(e.target.value)} 
                        required 
                        style={{ width: '100%' }}
                    />
                </div>

                <div>
                    <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, color: '#334155', marginBottom: '4px' }}>Password</label>
                    <input 
                        type="password" 
                        placeholder="••••••••" 
                        value={password} 
                        onChange={(e) => setPassword(e.target.value)} 
                        required 
                        style={{ width: '100%' }}
                    />
                </div>

                <button 
                    type="submit" 
                    className="btn-primary" 
                    disabled={loading}
                    style={{ padding: '10px', fontSize: '14px', marginTop: '6px' }}
                >
                    {loading ? 'Authenticating...' : 'Sign In'}
                </button>
            </form>

            <div style={{ marginTop: '24px', borderTop: '1px solid #f1f5f9', paddingTop: '16px' }}>
                <p style={{ fontSize: '12px', fontWeight: 600, color: '#475569', marginBottom: '8px' }}>Quick Fill Demo Accounts:</p>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                    <button 
                        type="button" 
                        onClick={() => fillDemo('guest@hotel.demo', 'Guest@12345')}
                        className="btn-secondary"
                        style={{ fontSize: '12px', padding: '6px 8px', textAlign: 'left' }}
                    >
                        👤 Guest Demo
                    </button>
                    <button 
                        type="button" 
                        onClick={() => fillDemo('reception@hotel.demo', 'Reception@12345')}
                        className="btn-secondary"
                        style={{ fontSize: '12px', padding: '6px 8px', textAlign: 'left' }}
                    >
                        📋 Receptionist
                    </button>
                    <button 
                        type="button" 
                        onClick={() => fillDemo('housekeeping@hotel.demo', 'Housekeep@12345')}
                        className="btn-secondary"
                        style={{ fontSize: '12px', padding: '6px 8px', textAlign: 'left' }}
                    >
                        🧹 Housekeeping
                    </button>
                    <button 
                        type="button" 
                        onClick={() => fillDemo('admin@hotel.demo', 'Admin@12345')}
                        className="btn-secondary"
                        style={{ fontSize: '12px', padding: '6px 8px', textAlign: 'left' }}
                    >
                        🛡️ Admin Demo
                    </button>
                </div>
            </div>

            <p style={{ marginTop: '20px', fontSize: '13px', color: '#64748b', textAlign: 'center' }}>
                Don't have an account? <Link to="/register" style={{ color: '#0d9488', fontWeight: 600 }}>Register here</Link>
            </p>
        </div>
    );
};

export default Login;
