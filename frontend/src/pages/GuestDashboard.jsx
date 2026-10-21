import React, { useState, useEffect, useContext } from 'react';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

const GuestDashboard = () => {
    const { user } = useContext(AuthContext);
    const [bookings, setBookings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [actionMessage, setActionMessage] = useState('');
    const navigate = useNavigate();

    const fetchMyBookings = async () => {
        try {
            const res = await api.get('/bookings/my');
            setBookings(res.data);
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchMyBookings();
    }, []);

    const handleCancel = async (bookingId) => {
        if (!window.confirm("Are you sure you want to cancel this reservation?")) return;
        try {
            await api.post(`/bookings/${bookingId}/cancel`);
            setActionMessage(`Reservation #${bookingId} has been cancelled successfully.`);
            fetchMyBookings();
        } catch (err) {
            alert(err.response?.data?.error || "Failed to cancel booking.");
        }
    };

    const handleViewInvoice = async (bookingId) => {
        try {
            const res = await api.get(`/billing/booking/${bookingId}`);
            if (res.data) {
                navigate(`/invoice/${res.data.invoiceId}`);
            }
        } catch (err) {
            alert("Invoice not yet generated or not found.");
        }
    };

    return (
        <div style={{ maxWidth: '1000px', margin: '0 auto', padding: '16px' }}>
            <div style={{ backgroundColor: '#ffffff', padding: '24px', borderRadius: '12px', border: '1px solid #e2e8f0', marginBottom: '24px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                        <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#0f172a' }}>Guest Reservation Portal</h1>
                        <p style={{ fontSize: '13px', color: '#64748b' }}>Account: {user?.fullName} ({user?.email})</p>
                    </div>
                    <button onClick={() => navigate('/search')} className="btn-primary" style={{ padding: '8px 16px', fontSize: '13px' }}>
                        + Book Another Stay
                    </button>
                </div>
            </div>

            {actionMessage && (
                <div style={{ backgroundColor: '#f0fdfa', border: '1px solid #ccfbf1', color: '#0f766e', padding: '12px 16px', borderRadius: '8px', marginBottom: '16px', fontSize: '13px' }}>
                    {actionMessage}
                </div>
            )}

            <div style={{ backgroundColor: '#ffffff', borderRadius: '12px', border: '1px solid #e2e8f0', overflow: 'hidden' }}>
                <div style={{ padding: '16px 20px', borderBottom: '1px solid #e2e8f0' }}>
                    <h2 style={{ fontSize: '16px', fontWeight: 600, color: '#0f172a' }}>My Reservations ({bookings.length})</h2>
                </div>

                {loading ? (
                    <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>Loading bookings...</div>
                ) : bookings.length === 0 ? (
                    <div style={{ padding: '40px', textAlign: 'center', color: '#94a3b8' }}>
                        <p>No reservations found.</p>
                        <button onClick={() => navigate('/search')} className="btn-primary" style={{ marginTop: '12px', fontSize: '13px' }}>
                            Explore Available Rooms
                        </button>
                    </div>
                ) : (
                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', textAlign: 'left' }}>
                            <thead>
                                <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569' }}>
                                    <th style={{ padding: '12px 16px' }}>Booking ID</th>
                                    <th style={{ padding: '12px 16px' }}>Room</th>
                                    <th style={{ padding: '12px 16px' }}>Check-In</th>
                                    <th style={{ padding: '12px 16px' }}>Check-Out</th>
                                    <th style={{ padding: '12px 16px' }}>Duration</th>
                                    <th style={{ padding: '12px 16px' }}>Total Amount</th>
                                    <th style={{ padding: '12px 16px' }}>Status</th>
                                    <th style={{ padding: '12px 16px', textAlign: 'right' }}>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {bookings.map(b => (
                                    <tr key={b.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                                        <td style={{ padding: '14px 16px', fontWeight: 600, color: '#0f172a' }}>#{b.id}</td>
                                        <td style={{ padding: '14px 16px' }}>
                                            <div style={{ fontWeight: 600 }}>Room {b.roomNumber}</div>
                                            <div style={{ fontSize: '11px', color: '#64748b' }}>{b.roomType}</div>
                                        </td>
                                        <td style={{ padding: '14px 16px' }}>{b.checkInDate}</td>
                                        <td style={{ padding: '14px 16px' }}>{b.checkOutDate}</td>
                                        <td style={{ padding: '14px 16px' }}>{b.numberOfNights} night{b.numberOfNights > 1 ? 's' : ''}</td>
                                        <td style={{ padding: '14px 16px', fontWeight: 600 }}>${b.totalAmount?.toFixed(2)}</td>
                                        <td style={{ padding: '14px 16px' }}>
                                            <span className={`badge badge-${b.status.toLowerCase().replace('_', '-')}`}>
                                                {b.status}
                                            </span>
                                        </td>
                                        <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                                            {b.status === 'CONFIRMED' && (
                                                <button 
                                                    onClick={() => handleCancel(b.id)}
                                                    className="btn-danger"
                                                    style={{ fontSize: '12px' }}
                                                >
                                                    Cancel
                                                </button>
                                            )}
                                            {b.status === 'CHECKED_OUT' && (
                                                <button 
                                                    onClick={() => handleViewInvoice(b.id)}
                                                    className="btn-secondary"
                                                    style={{ fontSize: '12px' }}
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
