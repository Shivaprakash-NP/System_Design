import React, { useState, useEffect, useContext } from 'react';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

const GuestDashboard = () => {
    const { user } = useContext(AuthContext);
    const [bookings, setBookings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [actionMessage, setActionMessage] = useState('');
    const [errorMessage, setErrorMessage] = useState('');
    const navigate = useNavigate();

    const fetchMyBookings = async () => {
        try {
            const res = await api.get('/bookings/my');
            setBookings(res.data);
        } catch (err) {
            console.error(err);
            setErrorMessage("Failed to load your reservations.");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchMyBookings();
    }, []);

    const handleCancel = async (bookingId) => {
        if (!window.confirm(`Are you sure you want to cancel reservation #${bookingId}?`)) return;
        setActionMessage('');
        setErrorMessage('');
        try {
            await api.post(`/bookings/${String(bookingId)}/cancel`);
            setActionMessage(`Reservation #${bookingId} has been cancelled successfully.`);
            fetchMyBookings();
        } catch (err) {
            setErrorMessage(err.response?.data?.error || "Failed to cancel booking.");
        }
    };

    const handleViewInvoice = async (bookingId) => {
        setActionMessage('');
        setErrorMessage('');
        try {
            const res = await api.get(`/billing/booking/${String(bookingId)}`);
            if (res.data) {
                navigate(`/invoice/${res.data.invoiceId}`);
            }
        } catch (err) {
            setErrorMessage("Stay invoice receipt has not yet been finalized.");
        }
    };

    const sortedBookings = [...bookings].sort((a, b) => {
        const da = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const db = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        if (db !== da) return db - da;
        return String(b.id).localeCompare(String(a.id));
    });

    return (
        <div style={{ maxWidth: '1180px', margin: '0 auto', padding: '24px 20px 48px' }}>
            <div style={{ 
                backgroundColor: 'var(--surface, #ffffff)', 
                padding: '28px 32px', 
                borderRadius: '16px', 
                border: '1px solid var(--border-color, #E5E7E1)', 
                marginBottom: '24px', 
                boxShadow: 'var(--shadow-sm)' 
            }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
                    <div>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', padding: '3px 8px', borderRadius: '100px', backgroundColor: 'var(--surface-muted, #F0F1EC)', color: 'var(--primary-brand, #174A3A)', fontSize: '11px', fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase', marginBottom: '8px' }}>
                            <span>🧳</span> Guest Portal
                        </div>
                        <h1 style={{ fontFamily: 'Playfair Display, serif', fontSize: '26px', fontWeight: 700, color: 'var(--text-primary, #252925)', margin: 0, letterSpacing: '-0.02em' }}>
                            My Reservations & Stays
                        </h1>
                        <p style={{ fontSize: '13px', color: 'var(--text-secondary, #70766F)', margin: '4px 0 0' }}>
                            Verified Account: <strong>{user?.fullName || 'Guest'}</strong> • {user?.email}
                        </p>
                    </div>
                    <button 
                        onClick={() => navigate('/search')} 
                        className="btn-primary" 
                        style={{ padding: '10px 20px', fontSize: '13px' }}
                    >
                        + Book Another Stay
                    </button>
                </div>

                {/* Guest Stats Summary */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginTop: '24px' }}>
                    <div style={{ 
                        padding: '16px 20px', 
                        borderRadius: '12px', 
                        backgroundColor: '#FFFFFF', 
                        border: '1px solid var(--border-color, #E5E7E1)',
                        boxShadow: 'var(--shadow-sm)'
                    }}>
                        <span style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
                            TOTAL RESERVATIONS
                        </span>
                        <div style={{ fontSize: '26px', fontWeight: 800, color: 'var(--primary-brand, #174A3A)', marginTop: '4px' }}>
                            {bookings.length}
                        </div>
                    </div>

                    <div style={{ 
                        padding: '16px 20px', 
                        borderRadius: '12px', 
                        backgroundColor: '#FFFFFF', 
                        border: '1px solid var(--border-color, #E5E7E1)',
                        boxShadow: 'var(--shadow-sm)'
                    }}>
                        <span style={{ fontSize: '11px', color: '#1d4ed8', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
                            ACTIVE STAYS
                        </span>
                        <div style={{ fontSize: '26px', fontWeight: 800, color: '#2563eb', marginTop: '4px' }}>
                            {bookings.filter(b => b.status === 'CONFIRMED' || b.status === 'CHECKED_IN').length}
                        </div>
                    </div>

                    <div style={{ 
                        padding: '16px 20px', 
                        borderRadius: '12px', 
                        backgroundColor: '#FFFFFF', 
                        border: '1px solid var(--border-color, #E5E7E1)',
                        boxShadow: 'var(--shadow-sm)'
                    }}>
                        <span style={{ fontSize: '11px', color: '#15803d', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
                            COMPLETED TRIPS
                        </span>
                        <div style={{ fontSize: '26px', fontWeight: 800, color: '#16a34a', marginTop: '4px' }}>
                            {bookings.filter(b => b.status === 'CHECKED_OUT').length}
                        </div>
                    </div>

                    <div style={{ 
                        padding: '16px 20px', 
                        borderRadius: '12px', 
                        backgroundColor: '#FFFFFF', 
                        border: '1px solid var(--border-color, #E5E7E1)',
                        boxShadow: 'var(--shadow-sm)'
                    }}>
                        <span style={{ fontSize: '11px', color: 'var(--accent-gold, #C5A46D)', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
                            TOTAL EXPENDITURE
                        </span>
                        <div style={{ fontSize: '26px', fontWeight: 800, color: 'var(--text-primary, #252925)', marginTop: '4px' }}>
                            ${bookings.filter(b => b.status !== 'CANCELLED').reduce((acc, b) => acc + (b.totalAmount || 0), 0).toFixed(2)}
                        </div>
                    </div>
                </div>
            </div>

            {actionMessage && (
                <div style={{ 
                    backgroundColor: '#ecfdf5', 
                    border: '1px solid #a7f3d0', 
                    color: '#065f46', 
                    padding: '12px 18px', 
                    borderRadius: '10px', 
                    marginBottom: '18px', 
                    fontSize: '13px', 
                    display: 'flex', 
                    justifyContent: 'space-between', 
                    alignItems: 'center' 
                }}>
                    <span>✓ {actionMessage}</span>
                    <button onClick={() => setActionMessage('')} style={{ background: 'none', border: 'none', color: '#065f46', fontWeight: 700, cursor: 'pointer' }}>✕</button>
                </div>
            )}

            {errorMessage && (
                <div style={{ 
                    backgroundColor: '#fef2f2', 
                    border: '1px solid #fecaca', 
                    color: '#991b1b', 
                    padding: '12px 18px', 
                    borderRadius: '10px', 
                    marginBottom: '18px', 
                    fontSize: '13px', 
                    display: 'flex', 
                    justifyContent: 'space-between', 
                    alignItems: 'center' 
                }}>
                    <span>⚠️ {errorMessage}</span>
                    <button onClick={() => setErrorMessage('')} style={{ background: 'none', border: 'none', color: '#991b1b', fontWeight: 700, cursor: 'pointer' }}>✕</button>
                </div>
            )}

            <div style={{ 
                backgroundColor: 'var(--surface, #ffffff)', 
                borderRadius: '16px', 
                border: '1px solid var(--border-color, #E5E7E1)', 
                overflow: 'hidden',
                boxShadow: 'var(--shadow-sm)' 
            }}>
                <div style={{ padding: '18px 24px', borderBottom: '1px solid var(--border-color, #E5E7E1)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <h2 style={{ fontFamily: 'Playfair Display, serif', fontSize: '18px', fontWeight: 700, color: 'var(--text-primary, #252925)', margin: 0 }}>
                        My Reservations ({bookings.length})
                    </h2>
                    <span style={{ fontSize: '12px', color: 'var(--text-secondary, #70766F)' }}>
                        All rates transparently include 12% GST
                    </span>
                </div>

                {loading ? (
                    <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-secondary, #70766F)' }}>Loading reservations...</div>
                ) : bookings.length === 0 ? (
                    <div style={{ padding: '56px 20px', textAlign: 'center', color: 'var(--text-secondary, #70766F)' }}>
                        <div style={{ fontSize: '36px', marginBottom: '12px' }}>🛎️</div>
                        <p style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary, #252925)', margin: 0 }}>No reservations found</p>
                        <p style={{ fontSize: '13px', marginTop: '4px', marginBottom: '16px' }}>Begin your journey with our handpicked accommodations.</p>
                        <button onClick={() => navigate('/search')} className="btn-primary" style={{ fontSize: '13px' }}>
                            Explore Available Rooms
                        </button>
                    </div>
                ) : (
                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', textAlign: 'left' }}>
                            <thead>
                                <tr style={{ backgroundColor: 'var(--surface-muted, #F0F1EC)', borderBottom: '1px solid var(--border-color, #E5E7E1)', color: 'var(--text-secondary, #70766F)' }}>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Booking ID</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Booked At</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Suite</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Check-In</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Check-Out</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Duration</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Total Amount</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Status</th>
                                    <th style={{ padding: '12px 18px', textAlign: 'right', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {sortedBookings.map(b => (
                                    <tr key={String(b.id)} style={{ borderBottom: '1px solid var(--border-color, #E5E7E1)', transition: 'background-color 0.15s ease' }}>
                                        <td style={{ padding: '14px 18px', fontWeight: 700, color: 'var(--primary-brand, #174A3A)', fontFamily: 'monospace' }}>
                                            #{String(b.id)}
                                        </td>
                                        <td style={{ padding: '14px 18px', fontSize: '12px', color: 'var(--text-secondary, #70766F)' }}>
                                            <div style={{ fontWeight: 600, color: 'var(--text-primary, #252925)' }}>
                                                {b.createdAt ? new Date(b.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : 'Recent'}
                                            </div>
                                            <div style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)', marginTop: '2px' }}>
                                                {b.createdAt ? new Date(b.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                                            </div>
                                        </td>
                                        <td style={{ padding: '14px 18px' }}>
                                            <div style={{ fontWeight: 700, color: 'var(--text-primary, #252925)' }}>Room {b.roomNumber}</div>
                                            <div style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{b.roomType} Suite</div>
                                        </td>
                                        <td style={{ padding: '14px 18px', color: 'var(--text-primary, #252925)' }}>{b.checkInDate}</td>
                                        <td style={{ padding: '14px 18px', color: 'var(--text-primary, #252925)' }}>{b.checkOutDate}</td>
                                        <td style={{ padding: '14px 18px', color: 'var(--text-secondary, #70766F)' }}>{b.numberOfNights} night{b.numberOfNights > 1 ? 's' : ''}</td>
                                        <td style={{ padding: '14px 18px' }}>
                                            <div style={{ fontWeight: 800, color: 'var(--text-primary, #252925)', fontSize: '14px' }}>
                                                ${b.totalAmount?.toFixed(2)}
                                            </div>
                                            <div style={{ fontSize: '11px', color: '#15803d', fontWeight: 600 }}>
                                                ✓ Includes 12% GST & fees
                                            </div>
                                        </td>
                                        <td style={{ padding: '14px 18px' }}>
                                            <span className={`badge badge-${b.status.toLowerCase().replace('_', '-')}`}>
                                                {b.status}
                                            </span>
                                        </td>
                                        <td style={{ padding: '14px 18px', textAlign: 'right' }}>
                                            {b.status === 'CONFIRMED' && (
                                                <button 
                                                    onClick={() => handleCancel(b.id)}
                                                    className="btn-danger"
                                                    style={{ fontSize: '12px', padding: '6px 12px' }}
                                                >
                                                    Cancel
                                                </button>
                                            )}
                                            {b.status === 'CHECKED_OUT' && (
                                                <button 
                                                    onClick={() => handleViewInvoice(b.id)}
                                                    className="btn-secondary"
                                                    style={{ fontSize: '12px', padding: '6px 12px' }}
                                                >
                                                    View Invoice
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
};

export default GuestDashboard;
