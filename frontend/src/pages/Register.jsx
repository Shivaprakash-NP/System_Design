import React, { useState, useContext } from 'react';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';
import { useNavigate, Link } from 'react-router-dom';

const Register = () => {
    const [fullName, setFullName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [phoneNumber, setPhoneNumber] = useState('');
    const [role, setRole] = useState('GUEST');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const { login } = useContext(AuthContext);
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const response = await api.post('/auth/register', {
                fullName,
                email,
                password,
                phoneNumber,
                role
            });
            login(response.data);
            const userRole = response.data?.role || role;
            if (userRole === 'RECEPTIONIST') {
                navigate('/reception');
            } else if (userRole === 'HOUSEKEEPING') {
                navigate('/housekeeping');
            } else {
                navigate('/search');
            }
        } catch (err) {
            setError(err.response?.data?.error || 'Registration failed.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ maxWidth: '460px', margin: '48px auto', padding: '36px 32px', backgroundColor: '#ffffff', borderRadius: '16px', boxShadow: 'var(--shadow-md)', border: '1px solid var(--border-color, #E5E7E1)' }}>
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
                Create an Account
            </h2>
            <p style={{ fontSize: '13px', color: 'var(--text-secondary, #70766F)', marginBottom: '24px', textAlign: 'center' }}>
                Register to book luxury stays or manage hotel operations
            </p>

            {error && (
                <div style={{ backgroundColor: '#fee2e2', color: '#b91c1c', padding: '10px 14px', borderRadius: '8px', fontSize: '13px', marginBottom: '18px', border: '1px solid #fecaca' }}>
                    {error}
                </div>
            )}

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, color: 'var(--text-primary, #252925)', marginBottom: '6px', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                        Full Name
                    </label>
                    <input 
                        type="text" 
                        placeholder="e.g. Ramesh Kumar" 
                        value={fullName} 
                        onChange={(e) => setFullName(e.target.value)} 
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
                        Email Address
                    </label>
                    <input 
                        type="email" 
                        placeholder="e.g. user@example.com" 
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

                <div>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, color: 'var(--text-primary, #252925)', marginBottom: '6px', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                        Phone Number
                    </label>
                    <input 
                        type="tel" 
                        placeholder="+91-9840112345" 
                        value={phoneNumber} 
                        onChange={(e) => setPhoneNumber(e.target.value)} 
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
                        Account Role
                    </label>
                    <select 
                        value={role} 
                        onChange={(e) => setRole(e.target.value)}
                        style={{ 
                            width: '100%',
                            padding: '11px 14px',
                            fontSize: '13px',
                            borderRadius: '8px',
                            border: '1px solid var(--border-color, #E5E7E1)',
                            backgroundColor: '#FFFFFF',
                            color: 'var(--text-primary, #252925)'
                        }}
                    >
                        <option value="GUEST">Guest (Customer Booking)</option>
                        <option value="RECEPTIONIST">Receptionist (Front Desk)</option>
                        <option value="HOUSEKEEPING">Housekeeping Staff</option>
                        <option value="ADMIN">Administrator</option>
                    </select>
                </div>

                <button 
                    type="submit" 
                    className="btn-primary" 
                    disabled={loading}
                    style={{ padding: '12px', fontSize: '14px', marginTop: '6px' }}
                >
                    {loading ? 'Creating Account...' : 'Register'}
                </button>
            </form>

            <p style={{ marginTop: '24px', fontSize: '13px', color: 'var(--text-secondary, #70766F)', textAlign: 'center' }}>
                Already registered? <Link to="/login" style={{ color: 'var(--primary-brand, #174A3A)', fontWeight: 700, textDecoration: 'none' }}>Sign in</Link>
            </p>
        </div>
    );
};

export default Register;
