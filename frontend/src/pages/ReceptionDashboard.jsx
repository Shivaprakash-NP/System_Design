import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { useNavigate } from 'react-router-dom';

const ReceptionDashboard = () => {
    const [bookings, setBookings] = useState([]);
    const [filterStatus, setFilterStatus] = useState('ALL');
    const [loading, setLoading] = useState(true);
    const [message, setMessage] = useState('');
    const navigate = useNavigate();

    const fetchAllBookings = async () => {
        try {
            const res = await api.get('/bookings');
            setBookings(res.data);
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchAllBookings();
    }, []);

    const handleCheckIn = async (bookingId) => {
        try {
            await api.post(`/bookings/${bookingId}/check-in`);
            setMessage(`Guest for Booking #${bookingId} checked in successfully!`);
            fetchAllBookings();
        } catch (err) {
            alert(err.response?.data?.error || "Check-in failed.");
        }
    };

    const handleCheckOut = async (bookingId) => {
        try {
            const res = await api.post(`/billing/checkout/${bookingId}`, {
                paymentMethod: 'SIMULATED_CARD'
            });
            setMessage(`Checkout completed for Booking #${bookingId}. Invoice #${res.data.invoiceId} generated. Room status set to DIRTY.`);
            fetchAllBookings();
        } catch (err) {
            alert(err.response?.data?.error || "Checkout failed.");
        }
    };

    const filteredBookings = filterStatus === 'ALL' 
        ? bookings 
        : bookings.filter(b => b.status === filterStatus);

    return (
        <div style={{ maxWidth: '1100px', margin: '0 auto', padding: '16px' }}>
            <div style={{ backgroundColor: '#ffffff', padding: '24px', borderRadius: '12px', border: '1px solid #e2e8f0', marginBottom: '24px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                        <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#0f172a' }}>Front Desk Reception Management</h1>
                        <p style={{ fontSize: '13px', color: '#64748b' }}>Check-in guests, verify stays, process checkouts, and generate itemized billing invoices.</p>
                    </div>
                    <div style={{ display: 'flex', gap: '8px' }}>
                        {['ALL', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT'].map(status => (
                            <button
                                key={status}
                                onClick={() => setFilterStatus(status)}
                                style={{
                                    padding: '6px 12px',
                                    borderRadius: '6px',
                                    fontSize: '12px',
                                    backgroundColor: filterStatus === status ? '#0f172a' : '#f1f5f9',
                                    color: filterStatus === status ? '#ffffff' : '#475569',
                                    fontWeight: filterStatus === status ? 600 : 500
                                }}
                            >
                                {status.replace('_', ' ')}
                            </button>
                        ))}
                    </div>
                </div>
            </div>

            {message && (
                <div style={{ backgroundColor: '#f0fdfa', border: '1px solid #ccfbf1', color: '#0f766e', padding: '12px 16px', borderRadius: '8px', marginBottom: '16px', fontSize: '13px' }}>
                    {message}
                </div>
            )}

            <div style={{ backgroundColor: '#ffffff', borderRadius: '12px', border: '1px solid #e2e8f0', overflow: 'hidden' }}>
                <div style={{ padding: '16px 20px', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <h2 style={{ fontSize: '16px', fontWeight: 600, color: '#0f172a' }}>
                        Guest Reservations ({filteredBookings.length})
                    </h2>
                    <button onClick={fetchAllBookings} className="btn-secondary" style={{ padding: '4px 10px', fontSize: '12px' }}>
                        ↻ Refresh
                    </button>
                </div>

                {loading ? (
                    <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>Loading records...</div>
                ) : filteredBookings.length === 0 ? (
                    <div style={{ padding: '40px', textAlign: 'center', color: '#94a3b8' }}>No bookings matching current filter.</div>
                ) : (
                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', textAlign: 'left' }}>
                            <thead>
                                <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569' }}>
                                    <th style={{ padding: '12px 16px' }}>Booking ID</th>
                                    <th style={{ padding: '12px 16px' }}>Guest Details</th>
                                    <th style={{ padding: '12px 16px' }}>Room</th>
                                    <th style={{ padding: '12px 16px' }}>Dates</th>
                                    <th style={{ padding: '12px 16px' }}>Amount</th>
                                    <th style={{ padding: '12px 16px' }}>Status</th>
                                    <th style={{ padding: '12px 16px', textAlign: 'right' }}>Workflow Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredBookings.map(b => (
                                    <tr key={b.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                                        <td style={{ padding: '14px 16px', fontWeight: 600, color: '#0f172a' }}>#{b.id}</td>
                                        <td style={{ padding: '14px 16px' }}>
                                            <div style={{ fontWeight: 600 }}>{b.guestName}</div>
                                            <div style={{ fontSize: '11px', color: '#64748b' }}>{b.guestEmail}</div>
                                        </td>
                                        <td style={{ padding: '14px 16px' }}>
                                            <div style={{ fontWeight: 600 }}>Room {b.roomNumber}</div>
                                            <div style={{ fontSize: '11px', color: '#64748b' }}>{b.roomType}</div>
                                        </td>
                                        <td style={{ padding: '14px 16px' }}>
                                            <div>{b.checkInDate} to {b.checkOutDate}</div>
                                            <div style={{ fontSize: '11px', color: '#64748b' }}>{b.numberOfNights} night{b.numberOfNights > 1 ? 's' : ''}</div>
                                        </td>
                                        <td style={{ padding: '14px 16px', fontWeight: 600 }}>${b.totalAmount?.toFixed(2)}</td>
                                        <td style={{ padding: '14px 16px' }}>
                                            <span className={`badge badge-${b.status.toLowerCase().replace('_', '-')}`}>
                                                {b.status}
                                            </span>
                                        </td>
                                        <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                                            {b.status === 'CONFIRMED' && (
                                                <button 
                                                    onClick={() => handleCheckIn(b.id)}
                                                    className="btn-primary"
                                                    style={{ fontSize: '12px', padding: '6px 12px' }}
                                                >
                                                    Check-In Guest
                                                </button>
                                            )}
                                            {b.status === 'CHECKED_IN' && (
                                                <button 
                                                    onClick={() => handleCheckOut(b.id)}
                                                    className="btn-gold"
                                                    style={{ fontSize: '12px', padding: '6px 12px' }}
                                                >
                                                    Process Check-Out & Bill
                                                </button>
                                            )}
                                            {b.status === 'CHECKED_OUT' && (
                                                <button 
                                                    onClick={async () => {
                                                        const res = await api.get(`/billing/booking/${b.id}`);
                                                        if (res.data) navigate(`/invoice/${res.data.invoiceId}`);
                                                    }}
                                                    className="btn-secondary"
                                                    style={{ fontSize: '12px', padding: '6px 10px' }}
                                                >
                                                    Receipt 📄
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
