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
        return <div style={{ padding: '80px', textAlign: 'center', color: 'var(--text-secondary, #70766F)' }}>Loading invoice receipt...</div>;
    }

    if (error || !invoice) {
        return (
            <div style={{ maxWidth: '600px', margin: '60px auto', textAlign: 'center', backgroundColor: '#ffffff', padding: '40px', borderRadius: '16px', border: '1px solid var(--border-color, #E5E7E1)', boxShadow: 'var(--shadow-sm)' }}>
                <div style={{ fontSize: '36px', marginBottom: '12px' }}>📄</div>
                <h2 style={{ fontFamily: 'Playfair Display, serif', color: '#b91c1c', margin: 0 }}>Invoice Not Found</h2>
                <p style={{ color: 'var(--text-secondary, #70766F)', margin: '12px 0 24px' }}>{error || "The requested bill could not be found."}</p>
                <Link to="/my-bookings" className="btn-primary" style={{ display: 'inline-block', textDecoration: 'none', padding: '10px 20px', fontSize: '13px' }}>
                    Back to Bookings
                </Link>
            </div>
        );
    }

    return (
        <div style={{ 
            maxWidth: '720px', 
            margin: '40px auto 60px', 
            backgroundColor: '#ffffff', 
            borderRadius: '16px', 
            padding: '40px 44px', 
            border: '1px solid var(--border-color, #E5E7E1)', 
            boxShadow: 'var(--shadow-md)' 
        }}>
            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', borderBottom: '1px solid var(--border-color, #E5E7E1)', paddingBottom: '24px', marginBottom: '28px' }}>
                <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
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
                        <h1 style={{ fontFamily: 'Playfair Display, serif', fontSize: '24px', fontWeight: 700, color: 'var(--text-primary, #252925)', margin: 0, letterSpacing: '-0.02em' }}>
                            Booking.com
                        </h1>
                    </div>
                    <p style={{ fontSize: '13px', color: 'var(--text-primary, #252925)', fontWeight: 600, margin: '2px 0' }}>Official Stay Receipt & Tax Invoice</p>
                    <p style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)', margin: 0 }}>Global Hospitality Partner Network • Electronic Billing Settlement</p>
                </div>
                <div style={{ textAlign: 'right' }}>
                    <span className="badge badge-paid" style={{ fontSize: '12px', padding: '4px 10px' }}>
                        ✓ {invoice.status}
                    </span>
                    <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--text-primary, #252925)', marginTop: '8px', fontFamily: 'monospace' }}>
                        INVOICE #{invoice.invoiceId}
                    </p>
                    <p style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)', margin: 0 }}>
                        {invoice.issuedAt ? new Date(invoice.issuedAt).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : ''}
                    </p>
                </div>
            </div>

            {/* Bill To & Stay Summary */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', marginBottom: '28px', backgroundColor: 'var(--surface-muted, #F0F1EC)', padding: '18px 20px', borderRadius: '12px', fontSize: '13px', border: '1px solid var(--border-color, #E5E7E1)' }}>
                <div>
                    <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-secondary, #70766F)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Billed To:</span>
                    <p style={{ fontWeight: 700, color: 'var(--text-primary, #252925)', margin: '6px 0 2px' }}>{invoice.guestName || 'Guest'}</p>
                    <p style={{ color: 'var(--text-secondary, #70766F)', margin: '0 0 2px' }}>{invoice.guestEmail}</p>
                    <p style={{ color: 'var(--text-secondary, #70766F)', fontSize: '11px', margin: 0 }}>Guest ID: {invoice.guestId}</p>
                </div>
                <div>
                    <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-secondary, #70766F)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Reservation Details:</span>
                    <p style={{ fontWeight: 700, color: 'var(--text-primary, #252925)', margin: '6px 0 2px' }}>Booking #{invoice.bookingId}</p>
                    <p style={{ color: 'var(--text-secondary, #70766F)', margin: '0 0 2px' }}>Room {invoice.roomNumber} ({invoice.roomType})</p>
                    <p style={{ color: 'var(--text-secondary, #70766F)', fontSize: '11px', margin: 0 }}>Payment: {invoice.paymentMethod || 'Simulated Card Settlement'}</p>
                </div>
            </div>

            {/* Itemized Table */}
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', marginBottom: '28px' }}>
                <thead>
                    <tr style={{ borderBottom: '2px solid var(--border-color, #E5E7E1)', color: 'var(--text-secondary, #70766F)', textAlign: 'left' }}>
                        <th style={{ padding: '10px 0', textTransform: 'uppercase', fontSize: '11px', letterSpacing: '0.06em' }}>Item Description</th>
                        <th style={{ padding: '10px 0', textAlign: 'right', textTransform: 'uppercase', fontSize: '11px', letterSpacing: '0.06em' }}>Amount</th>
                    </tr>
                </thead>
                <tbody>
                    <tr style={{ borderBottom: '1px solid var(--border-color, #E5E7E1)' }}>
                        <td style={{ padding: '14px 0' }}>
                            <div style={{ fontWeight: 700, color: 'var(--text-primary, #252925)' }}>Accommodation Charges</div>
                            <div style={{ fontSize: '12px', color: 'var(--text-secondary, #70766F)' }}>Room #{invoice.roomNumber} Stay ({invoice.roomType} Suite)</div>
                        </td>
                        <td style={{ padding: '14px 0', textAlign: 'right', fontWeight: 600, color: 'var(--text-primary, #252925)' }}>
                            ${invoice.roomCharges?.toFixed(2)}
                        </td>
                    </tr>
                    <tr style={{ borderBottom: '1px solid var(--border-color, #E5E7E1)' }}>
                        <td style={{ padding: '12px 0', color: 'var(--text-secondary, #70766F)' }}>Government Hotel Tax (12% GST)</td>
                        <td style={{ padding: '12px 0', textAlign: 'right', fontWeight: 600, color: 'var(--accent-gold, #C5A46D)' }}>${invoice.taxAmount?.toFixed(2)}</td>
                    </tr>
                    <tr style={{ borderBottom: '1px solid var(--border-color, #E5E7E1)' }}>
                        <td style={{ padding: '12px 0', color: 'var(--text-secondary, #70766F)' }}>Facility & Service Fee</td>
                        <td style={{ padding: '12px 0', textAlign: 'right', fontWeight: 600, color: 'var(--text-secondary, #70766F)' }}>${invoice.serviceCharges?.toFixed(2)}</td>
                    </tr>
                    <tr style={{ borderTop: '2px solid var(--primary-brand, #174A3A)' }}>
                        <td style={{ padding: '16px 0', fontWeight: 800, fontSize: '15px', color: 'var(--text-primary, #252925)' }}>Total Paid</td>
                        <td style={{ padding: '16px 0', textAlign: 'right', fontWeight: 800, fontSize: '20px', color: 'var(--primary-brand, #174A3A)' }}>
                            ${invoice.totalAmount?.toFixed(2)}
                        </td>
                    </tr>
                </tbody>
            </table>

            {/* URL Shortener Notification Link */}
            {invoice.invoiceUrl && (
                <div style={{ 
                    backgroundColor: 'rgba(23, 74, 58, 0.05)', 
                    border: '1px solid rgba(23, 74, 58, 0.2)', 
                    padding: '14px 18px', 
                    borderRadius: '10px', 
                    marginBottom: '28px', 
                    display: 'flex', 
                    justifyContent: 'space-between', 
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '12px'
                }}>
                    <div>
                        <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--primary-brand, #174A3A)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>🔗 Shortened Invoice Link (Base62 URL Shortener)</span>
                        <p style={{ fontSize: '13px', color: 'var(--text-primary, #252925)', margin: '3px 0 0', fontWeight: 500, fontFamily: 'monospace' }}>
                            http://localhost:8080{invoice.invoiceUrl}
                        </p>
                    </div>
                    <button 
                        onClick={() => {
                            navigator.clipboard.writeText(`http://localhost:8080${invoice.invoiceUrl}`);
                            alert("Short URL copied to clipboard!");
                        }}
                        className="btn-primary"
                        style={{ padding: '7px 16px', fontSize: '12px' }}
                    >
                        Copy Link
                    </button>
                </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid var(--border-color, #E5E7E1)', paddingTop: '18px' }}>
                <Link to="/my-bookings" style={{ color: 'var(--text-secondary, #70766F)', fontSize: '13px', textDecoration: 'none', fontWeight: 600 }}>
                    ← Back to Reservations
                </Link>
                <button 
                    onClick={() => window.print()}
                    className="btn-secondary"
                    style={{ padding: '8px 18px', fontSize: '13px' }}
                >
                    Print Receipt 🖨️
                </button>
            </div>
        </div>
    );
};

export default InvoiceView;
