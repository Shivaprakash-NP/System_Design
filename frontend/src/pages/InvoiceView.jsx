import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import api from '../services/api';

const InvoiceView = () => {
    const { invoiceId } = useParams();
    const [invoice, setInvoice] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        const fetchInvoice = async () => {
            try {
                const res = await api.get(`/billing/invoice/${invoiceId}`);
                setInvoice(res.data);
            } catch (err) {
                setError("Invoice not found or invalid identifier.");
            } finally {
                setLoading(false);
            }
        };

        if (invoiceId) {
            fetchInvoice();
        }
    }, [invoiceId]);

    if (loading) {
        return <div style={{ padding: '60px', textAlign: 'center', color: '#64748b' }}>Loading invoice receipt...</div>;
    }

    if (error || !invoice) {
        return (
            <div style={{ maxWidth: '600px', margin: '40px auto', textAlign: 'center', backgroundColor: '#ffffff', padding: '32px', borderRadius: '12px', border: '1px solid #e2e8f0' }}>
                <h2 style={{ color: '#b91c1c' }}>Invoice Not Found</h2>
                <p style={{ color: '#64748b', margin: '12px 0' }}>{error || "The requested bill could not be found."}</p>
                <Link to="/my-bookings" className="btn-primary" style={{ display: 'inline-block', textDecoration: 'none', padding: '8px 16px' }}>
                    Back to Bookings
                </Link>
            </div>
        );
    }

    return (
        <div style={{ maxWidth: '680px', margin: '40px auto', backgroundColor: '#ffffff', borderRadius: '12px', padding: '36px', border: '1px solid #e2e8f0', boxShadow: '0 4px 20px rgba(0,0,0,0.06)' }}>
            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', borderBottom: '2px solid #f1f5f9', paddingBottom: '20px', marginBottom: '24px' }}>
                <div>
                    <h1 style={{ fontSize: '24px', fontWeight: 800, color: '#0f172a' }}>SSN GRAND HOTEL</h1>
                    <p style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>UCS3513 Distributed System Design Prototype</p>
                    <p style={{ fontSize: '12px', color: '#64748b' }}>Guindy Tech Corridor, Chennai, India</p>
                </div>
                <div style={{ textAlign: 'right' }}>
                    <span className="badge badge-paid" style={{ fontSize: '13px', padding: '4px 10px' }}>
                        ✓ {invoice.status}
                    </span>
                    <p style={{ fontSize: '13px', fontWeight: 700, color: '#0f172a', marginTop: '6px' }}>INVOICE #{invoice.invoiceId}</p>
                    <p style={{ fontSize: '11px', color: '#64748b' }}>
                        {invoice.issuedAt ? new Date(invoice.issuedAt).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : ''}
                    </p>
                </div>
            </div>

            {/* Bill To & Stay Summary */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', marginBottom: '24px', backgroundColor: '#f8fafc', padding: '16px', borderRadius: '8px', fontSize: '13px' }}>
                <div>
                    <span style={{ fontSize: '11px', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>Billed To:</span>
                    <p style={{ fontWeight: 700, color: '#0f172a', marginTop: '4px' }}>{invoice.guestName || 'Guest'}</p>
                    <p style={{ color: '#475569' }}>{invoice.guestEmail}</p>
                    <p style={{ color: '#64748b', fontSize: '12px' }}>Guest ID: {invoice.guestId}</p>
                </div>
                <div>
                    <span style={{ fontSize: '11px', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>Reservation Details:</span>
                    <p style={{ fontWeight: 700, color: '#0f172a', marginTop: '4px' }}>Booking #{invoice.bookingId}</p>
                    <p style={{ color: '#475569' }}>Room {invoice.roomNumber} ({invoice.roomType})</p>
                    <p style={{ color: '#64748b', fontSize: '12px' }}>Payment: {invoice.paymentMethod || 'Simulated Payment'}</p>
                </div>
            </div>

            {/* Itemized Table */}
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px', marginBottom: '24px' }}>
                <thead>
                    <tr style={{ borderBottom: '2px solid #e2e8f0', color: '#475569', textAlign: 'left' }}>
                        <th style={{ padding: '10px 0' }}>Item Description</th>
                        <th style={{ padding: '10px 0', textAlign: 'right' }}>Amount</th>
                    </tr>
                </thead>
                <tbody>
                    <tr style={{ borderBottom: '1px solid #f1f5f9' }}>
                        <td style={{ padding: '12px 0' }}>
                            <div style={{ fontWeight: 600, color: '#0f172a' }}>Accommodation Charges</div>
                            <div style={{ fontSize: '12px', color: '#64748b' }}>Room #{invoice.roomNumber} Stay</div>
                        </td>
                        <td style={{ padding: '12px 0', textAlign: 'right', fontWeight: 600 }}>
                            ${invoice.roomCharges?.toFixed(2)}
                        </td>
                    </tr>
                    <tr style={{ borderBottom: '1px solid #f1f5f9' }}>
                        <td style={{ padding: '12px 0', color: '#475569' }}>Government Hotel Tax (12% GST)</td>
                        <td style={{ padding: '12px 0', textAlign: 'right' }}>${invoice.taxAmount?.toFixed(2)}</td>
                    </tr>
                    <tr style={{ borderBottom: '1px solid #f1f5f9' }}>
                        <td style={{ padding: '12px 0', color: '#475569' }}>Facility & Service Fee</td>
                        <td style={{ padding: '12px 0', textAlign: 'right' }}>${invoice.serviceCharges?.toFixed(2)}</td>
                    </tr>
                    <tr style={{ borderTop: '2px solid #0f172a' }}>
                        <td style={{ padding: '14px 0', fontWeight: 800, fontSize: '16px', color: '#0f172a' }}>Total Paid</td>
                        <td style={{ padding: '14px 0', textAlign: 'right', fontWeight: 800, fontSize: '18px', color: '#0d9488' }}>
                            ${invoice.totalAmount?.toFixed(2)}
                        </td>
                    </tr>
                </tbody>
            </table>

            {/* URL Shortener Notification Link */}
            {invoice.invoiceUrl && (
                <div style={{ backgroundColor: '#f0fdfa', border: '1px solid #ccfbf1', padding: '12px 16px', borderRadius: '8px', marginBottom: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                        <span style={{ fontSize: '11px', fontWeight: 700, color: '#0f766e', textTransform: 'uppercase' }}>🔗 Shortened Invoice Link (Base62 URL Shortener)</span>
                        <p style={{ fontSize: '13px', color: '#0f766e', marginTop: '2px' }}>
                            http://localhost:8080{invoice.invoiceUrl}
                        </p>
                    </div>
                    <button 
                        onClick={() => {
                            navigator.clipboard.writeText(`http://localhost:8080${invoice.invoiceUrl}`);
                            alert("Short URL copied to clipboard!");
                        }}
                        style={{ padding: '4px 10px', fontSize: '12px', backgroundColor: '#0d9488', color: '#ffffff', borderRadius: '4px' }}
                    >
                        Copy Link
                    </button>
                </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid #f1f5f9', paddingTop: '16px' }}>
                <Link to="/my-bookings" style={{ color: '#64748b', fontSize: '13px', textDecoration: 'none' }}>
                    ← Back to Reservations
                </Link>
                <button 
                    onClick={() => window.print()}
                    className="btn-secondary"
                    style={{ padding: '6px 14px', fontSize: '13px' }}
                >
                    Print Receipt 🖨️
                </button>
            </div>
        </div>
    );
};

export default InvoiceView;
