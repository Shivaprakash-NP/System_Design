import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { useNavigate } from 'react-router-dom';

const ReceptionDashboard = () => {
    const [bookings, setBookings] = useState([]);
    const [filterStatus, setFilterStatus] = useState('ALL');
    const [searchQuery, setSearchQuery] = useState('');
    const [loading, setLoading] = useState(true);
    const [message, setMessage] = useState('');
    const [errorMessage, setErrorMessage] = useState('');
    const [actionLoading, setActionLoading] = useState(null);
    const navigate = useNavigate();

    const fetchAllBookings = async () => {
        try {
            const res = await api.get('/bookings');
            setBookings(res.data);
        } catch (err) {
            console.error(err);
            setErrorMessage("Failed to load booking reservations. Check network connection.");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchAllBookings();
        const interval = setInterval(fetchAllBookings, 4000);
        return () => clearInterval(interval);
    }, []);

    const handleCheckIn = async (bookingId) => {
        setActionLoading(String(bookingId));
        setMessage('');
        setErrorMessage('');
        try {
            await api.post(`/bookings/${String(bookingId)}/check-in`);
            setMessage(`✓ Guest for Booking #${bookingId} checked in successfully!`);
            fetchAllBookings();
        } catch (err) {
            setErrorMessage(err.response?.data?.error || `Check-in failed for booking #${bookingId}.`);
        } finally {
            setActionLoading(null);
        }
    };

    const handleCheckOut = async (bookingId) => {
        setActionLoading(String(bookingId));
        setMessage('');
        setErrorMessage('');
        try {
            const res = await api.post(`/billing/checkout/${String(bookingId)}`, {
                paymentMethod: 'SIMULATED_CARD'
            });
            setMessage(`✓ Checkout completed for Booking #${bookingId}. Stay Invoice #${res.data.invoiceId} generated. Room marked DIRTY for housekeeping turnover.`);
            fetchAllBookings();
        } catch (err) {
            setErrorMessage(err.response?.data?.error || `Checkout failed for booking #${bookingId}.`);
        } finally {
            setActionLoading(null);
        }
    };

    const sortedBookings = [...bookings].sort((a, b) => {
        const da = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const db = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        if (db !== da) return db - da;
        return String(b.id).localeCompare(String(a.id));
    });

    const filteredBookings = sortedBookings.filter(b => {
        const matchesStatus = filterStatus === 'ALL' || b.status === filterStatus;
        const matchesSearch = !searchQuery.trim() || 
            String(b.id).includes(searchQuery.trim()) ||
            (b.guestName && b.guestName.toLowerCase().includes(searchQuery.toLowerCase().trim())) ||
            (b.guestEmail && b.guestEmail.toLowerCase().includes(searchQuery.toLowerCase().trim())) ||
            (b.roomNumber && String(b.roomNumber).includes(searchQuery.trim()));
        return matchesStatus && matchesSearch;
    });

    return (
        <div style={{ maxWidth: '1240px', margin: '0 auto', padding: '24px 20px 48px' }}>
            {/* Header */}
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
                            <span>📋</span> Hospitality Desk
                        </div>
                        <h1 style={{ fontFamily: 'Playfair Display, serif', fontSize: '26px', fontWeight: 700, color: 'var(--text-primary, #252925)', margin: 0, letterSpacing: '-0.02em' }}>
                            Front Desk Operations
                        </h1>
                        <p style={{ fontSize: '13px', color: 'var(--text-secondary, #70766F)', margin: '4px 0 0' }}>
                            Manage guest arrivals, check-ins, departures, room turnovers, and automated GST billing receipts
                        </p>
                    </div>

                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                        {['ALL', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT'].map(status => (
                            <button
                                key={status}
                                type="button"
                                onClick={() => setFilterStatus(status)}
                                className={`tab-pill ${filterStatus === status ? 'active' : ''}`}
                                style={{ fontSize: '12px', padding: '6px 14px' }}
                            >
                                {status.replace('_', ' ')}
                            </button>
                        ))}
                    </div>
                </div>

                {/* KPI Cards */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginTop: '24px' }}>
                    <div style={{ padding: '16px 20px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1px solid var(--border-color, #E5E7E1)', boxShadow: 'var(--shadow-sm)' }}>
                        <span style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>TOTAL BOOKINGS</span>
                        <div style={{ fontSize: '26px', fontWeight: 800, color: 'var(--primary-brand, #174A3A)', marginTop: '4px' }}>{bookings.length}</div>
                    </div>
                    <div style={{ padding: '16px 20px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1px solid var(--border-color, #E5E7E1)', boxShadow: 'var(--shadow-sm)' }}>
                        <span style={{ fontSize: '11px', color: '#1d4ed8', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>CONFIRMED ARRIVALS</span>
                        <div style={{ fontSize: '26px', fontWeight: 800, color: '#2563eb', marginTop: '4px' }}>
                            {bookings.filter(b => b.status === 'CONFIRMED').length}
                        </div>
                    </div>
                    <div style={{ padding: '16px 20px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1px solid var(--border-color, #E5E7E1)', boxShadow: 'var(--shadow-sm)' }}>
                        <span style={{ fontSize: '11px', color: '#15803d', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>IN-HOUSE GUESTS</span>
                        <div style={{ fontSize: '26px', fontWeight: 800, color: '#16a34a', marginTop: '4px' }}>
                            {bookings.filter(b => b.status === 'CHECKED_IN').length}
                        </div>
                    </div>
                    <div style={{ padding: '16px 20px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1px solid var(--border-color, #E5E7E1)', boxShadow: 'var(--shadow-sm)' }}>
                        <span style={{ fontSize: '11px', color: 'var(--accent-gold, #C5A46D)', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>COMPLETED CHECKOUTS</span>
                        <div style={{ fontSize: '26px', fontWeight: 800, color: 'var(--text-primary, #252925)', marginTop: '4px' }}>
                            {bookings.filter(b => b.status === 'CHECKED_OUT').length}
                        </div>
                    </div>
                </div>

                {/* Search Bar */}
                <div style={{ marginTop: '20px', display: 'flex', gap: '12px', alignItems: 'center' }}>
                    <input 
                        type="text"
                        placeholder="Search reservations by Booking ID, Guest Name, Email, or Room..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        style={{ 
                            flex: 1, 
                            padding: '11px 16px', 
                            fontSize: '13px', 
                            borderRadius: '8px', 
                            border: '1px solid var(--border-color, #E5E7E1)',
                            backgroundColor: '#FFFFFF',
                            color: 'var(--text-primary, #252925)'
                        }}
                    />
                    {searchQuery && (
                        <button 
                            onClick={() => setSearchQuery('')}
                            className="btn-secondary"
                            style={{ padding: '10px 16px', fontSize: '12px' }}
                        >
                            Clear
                        </button>
                    )}
                </div>
            </div>

            {/* Notification Banners */}
            {message && (
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
                    <span>{message}</span>
                    <button onClick={() => setMessage('')} style={{ background: 'none', border: 'none', color: '#065f46', fontWeight: 700, cursor: 'pointer', fontSize: '14px' }}>✕</button>
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
                    <button onClick={() => setErrorMessage('')} style={{ background: 'none', border: 'none', color: '#991b1b', fontWeight: 700, cursor: 'pointer', fontSize: '14px' }}>✕</button>
                </div>
            )}

            {/* Bookings Table */}
            <div style={{ 
                backgroundColor: 'var(--surface, #ffffff)', 
                borderRadius: '16px', 
                border: '1px solid var(--border-color, #E5E7E1)', 
                overflow: 'hidden', 
                boxShadow: 'var(--shadow-sm)' 
            }}>
                <div style={{ padding: '18px 24px', borderBottom: '1px solid var(--border-color, #E5E7E1)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <h2 style={{ fontFamily: 'Playfair Display, serif', fontSize: '18px', fontWeight: 700, color: 'var(--text-primary, #252925)', margin: 0 }}>
                        Guest Reservations ({filteredBookings.length})
                    </h2>
                    <button onClick={fetchAllBookings} className="btn-secondary" style={{ padding: '6px 14px', fontSize: '12px' }}>
                        ↻ Refresh Table
                    </button>
                </div>

                {loading ? (
                    <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-secondary, #70766F)' }}>Loading records...</div>
                ) : filteredBookings.length === 0 ? (
                    <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-secondary, #70766F)' }}>
                        <div style={{ fontSize: '32px', marginBottom: '8px' }}>🔍</div>
                        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-primary, #252925)' }}>No reservations matching current filter.</p>
                    </div>
                ) : (
                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', textAlign: 'left' }}>
                            <thead>
                                <tr style={{ backgroundColor: 'var(--surface-muted, #F0F1EC)', borderBottom: '1px solid var(--border-color, #E5E7E1)', color: 'var(--text-secondary, #70766F)' }}>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Booking ID</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Booked At</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Guest Details</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Suite</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Stay Dates</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Amount</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Status</th>
                                    <th style={{ padding: '12px 18px', textAlign: 'right', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Front Desk Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredBookings.map(b => (
                                    <tr key={String(b.id)} style={{ borderBottom: '1px solid var(--border-color, #E5E7E1)', transition: 'background-color 0.15s ease' }}>
                                        <td style={{ padding: '14px 18px', fontWeight: 700, color: 'var(--primary-brand, #174A3A)', fontFamily: 'monospace', fontSize: '12px' }}>
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
                                            <div style={{ fontWeight: 700, color: 'var(--text-primary, #252925)' }}>{b.guestName}</div>
                                            <div style={{ fontSize: '12px', color: 'var(--text-secondary, #70766F)' }}>{b.guestEmail}</div>
                                        </td>
                                        <td style={{ padding: '14px 18px' }}>
                                            <div style={{ fontWeight: 700, color: 'var(--text-primary, #252925)' }}>Room {b.roomNumber}</div>
                                            <div style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{b.roomType} Suite</div>
                                        </td>
                                        <td style={{ padding: '14px 18px' }}>
                                            <div style={{ color: 'var(--text-primary, #252925)' }}>{b.checkInDate} to {b.checkOutDate}</div>
                                            <div style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)' }}>{b.numberOfNights} night{b.numberOfNights > 1 ? 's' : ''}</div>
                                        </td>
                                        <td style={{ padding: '14px 18px' }}>
                                            <div style={{ fontWeight: 800, color: 'var(--text-primary, #252925)', fontSize: '14px' }}>
                                                ${b.totalAmount?.toFixed(2)}
                                            </div>
                                            <div style={{ fontSize: '11px', color: '#15803d', fontWeight: 600 }}>
                                                incl. GST & fees
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
                                                    onClick={() => handleCheckIn(b.id)}
                                                    disabled={actionLoading === String(b.id)}
                                                    className="btn-primary"
                                                    style={{ fontSize: '12px', padding: '7px 14px' }}
                                                >
                                                    {actionLoading === String(b.id) ? 'Checking In...' : 'Check-In Guest'}
                                                </button>
                                            )}
                                            {b.status === 'CHECKED_IN' && (
                                                <button 
                                                    onClick={() => handleCheckOut(b.id)}
                                                    disabled={actionLoading === String(b.id)}
                                                    className="btn-gold"
                                                    style={{ fontSize: '12px', padding: '7px 14px' }}
                                                >
                                                    {actionLoading === String(b.id) ? 'Processing...' : 'Process Check-Out & Bill'}
                                                </button>
                                            )}
                                            {b.status === 'CHECKED_OUT' && (
                                                <button 
                                                    onClick={async () => {
                                                        const res = await api.get(`/billing/booking/${String(b.id)}`);
                                                        if (res.data) navigate(`/invoice/${res.data.invoiceId}`);
                                                    }}
                                                    className="btn-secondary"
                                                    style={{ fontSize: '12px', padding: '7px 14px' }}
                                                >
                                                    Stay Receipt 📄
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

export default ReceptionDashboard;
