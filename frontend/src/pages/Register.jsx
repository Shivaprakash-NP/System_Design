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
            navigate('/search');
        } catch (err) {
            setError(err.response?.data?.error || 'Registration failed.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ maxWidth: '440px', margin: '40px auto', padding: '28px', backgroundColor: '#ffffff', borderRadius: '12px', boxShadow: '0 4px 20px rgba(0,0,0,0.06)', border: '1px solid #e2e8f0' }}>
            <h2 style={{ fontSize: '22px', fontWeight: 700, color: '#0f172a', marginBottom: '8px' }}>Create an Account</h2>
            <p style={{ fontSize: '13px', color: '#64748b', marginBottom: '20px' }}>Register to book luxury stays or manage hotel operations.</p>

            {error && (
                <div style={{ backgroundColor: '#fee2e2', color: '#b91c1c', padding: '10px 14px', borderRadius: '6px', fontSize: '13px', marginBottom: '16px' }}>
                    {error}
                </div>
            )}

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div>
                    <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, color: '#334155', marginBottom: '4px' }}>Full Name</label>
                    <input 
                        type="text" 
                        placeholder="e.g. Ramesh Kumar" 
                        value={fullName} 
                        onChange={(e) => setFullName(e.target.value)} 
                        required 
                        style={{ width: '100%' }}
                    />
                </div>

                <div>
                    <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, color: '#334155', marginBottom: '4px' }}>Email Address</label>
                    <input 
                        type="email" 
                        placeholder="e.g. user@example.com" 
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

                <div>
                    <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, color: '#334155', marginBottom: '4px' }}>Phone Number</label>
                    <input 
                        type="tel" 
                        placeholder="+91-9840112345" 
                        value={phoneNumber} 
                        onChange={(e) => setPhoneNumber(e.target.value)} 
                        style={{ width: '100%' }}
                    />
                </div>

                <div>
                    <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, color: '#334155', marginBottom: '4px' }}>Account Role</label>
                    <select 
                        value={role} 
                        onChange={(e) => setRole(e.target.value)}
                        style={{ width: '100%' }}
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
                    style={{ padding: '10px', fontSize: '14px', marginTop: '6px' }}
                >
                    {loading ? 'Creating Account...' : 'Register'}
                </button>
            </form>

            <p style={{ marginTop: '20px', fontSize: '13px', color: '#64748b', textAlign: 'center' }}>
                Already registered? <Link to="/login" style={{ color: '#0d9488', fontWeight: 600 }}>Sign in</Link>
            </p>
        </div>
    );
};

export default Register;
