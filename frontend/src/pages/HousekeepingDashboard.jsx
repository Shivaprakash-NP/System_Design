import React, { useState, useEffect } from 'react';
import api from '../services/api';

const HousekeepingDashboard = () => {
    const [rooms, setRooms] = useState([]);
    const [summary, setSummary] = useState({});
    const [loading, setLoading] = useState(true);
    const [filterStatus, setFilterStatus] = useState('ALL');
    const [staffNames, setStaffNames] = useState({});
    const [message, setMessage] = useState('');
    const [errorMessage, setErrorMessage] = useState('');

    const fetchData = async () => {
        try {
            const [roomsRes, summaryRes] = await Promise.all([
                api.get('/housekeeping/rooms'),
                api.get('/housekeeping/summary')
            ]);
            setRooms(roomsRes.data);
            setSummary(summaryRes.data);
        } catch (err) {
            console.error(err);
            setErrorMessage("Failed to load room housekeeping inventory.");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    const handleStatusChange = async (roomId, newStatus) => {
        setMessage('');
        setErrorMessage('');
        try {
            const assignedStaff = staffNames[roomId] || 'Assigned Staff';
            await api.put(`/housekeeping/${roomId}/status`, {
                status: newStatus,
                assignedStaff
            });
            setMessage(`✓ Room updated to ${newStatus} successfully.`);
            fetchData();
        } catch (err) {
            setErrorMessage(err.response?.data?.error || "Status update failed.");
        }
    };

    const filteredRooms = filterStatus === 'ALL'
        ? rooms
        : rooms.filter(r => r.housekeepingStatus === filterStatus);

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
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                    <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', padding: '3px 8px', borderRadius: '100px', backgroundColor: 'var(--surface-muted, #F0F1EC)', color: 'var(--primary-brand, #174A3A)', fontSize: '11px', fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase' }}>
                        <span>🧹</span> Hospitality Operations
                    </div>
                </div>
                <h1 style={{ fontFamily: 'Playfair Display, serif', fontSize: '26px', fontWeight: 700, color: 'var(--text-primary, #252925)', margin: 0, letterSpacing: '-0.02em' }}>
                    Housekeeping & Operational Status
                </h1>
                <p style={{ fontSize: '13px', color: 'var(--text-secondary, #70766F)', margin: '4px 0 0' }}>
                    Manage room turnovers, sanitization protocols, staff assignments, and quality inspections across all suites
                </p>

                {/* KPI Cards */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '14px', marginTop: '24px' }}>
                    <div style={{ padding: '16px 20px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1px solid var(--border-color, #E5E7E1)', boxShadow: 'var(--shadow-sm)' }}>
                        <span style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>TOTAL INVENTORY</span>
                        <div style={{ fontSize: '24px', fontWeight: 800, color: 'var(--text-primary, #252925)', marginTop: '4px' }}>{rooms.length} Rooms</div>
                    </div>
                    <div style={{ padding: '16px 20px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1px solid var(--border-color, #E5E7E1)', boxShadow: 'var(--shadow-sm)' }}>
                        <span style={{ fontSize: '11px', color: '#15803d', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>CLEAN & READY</span>
                        <div style={{ fontSize: '24px', fontWeight: 800, color: '#16a34a', marginTop: '4px' }}>{summary['CLEAN'] || 0}</div>
                    </div>
                    <div style={{ padding: '16px 20px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1px solid var(--border-color, #E5E7E1)', boxShadow: 'var(--shadow-sm)' }}>
                        <span style={{ fontSize: '11px', color: '#b91c1c', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>DIRTY (TURNOVER)</span>
                        <div style={{ fontSize: '24px', fontWeight: 800, color: '#dc2626', marginTop: '4px' }}>{summary['DIRTY'] || 0}</div>
                    </div>
                    <div style={{ padding: '16px 20px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1px solid var(--border-color, #E5E7E1)', boxShadow: 'var(--shadow-sm)' }}>
                        <span style={{ fontSize: '11px', color: 'var(--accent-gold, #C5A46D)', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>IN PROGRESS</span>
                        <div style={{ fontSize: '24px', fontWeight: 800, color: '#d97706', marginTop: '4px' }}>{summary['CLEANING'] || 0}</div>
                    </div>
                    <div style={{ padding: '16px 20px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1px solid var(--border-color, #E5E7E1)', boxShadow: 'var(--shadow-sm)' }}>
                        <span style={{ fontSize: '11px', color: '#0369a1', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>INSPECTED</span>
                        <div style={{ fontSize: '24px', fontWeight: 800, color: '#0284c7', marginTop: '4px' }}>{summary['INSPECTED'] || 0}</div>
                    </div>
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

            {/* Room List Table */}
            <div style={{ 
                backgroundColor: 'var(--surface, #ffffff)', 
                borderRadius: '16px', 
                border: '1px solid var(--border-color, #E5E7E1)', 
                overflow: 'hidden',
                boxShadow: 'var(--shadow-sm)'
            }}>
                <div style={{ padding: '18px 24px', borderBottom: '1px solid var(--border-color, #E5E7E1)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                        {['ALL', 'DIRTY', 'CLEANING', 'CLEAN', 'INSPECTED'].map(st => (
                            <button
                                key={st}
                                onClick={() => setFilterStatus(st)}
                                className={`tab-pill ${filterStatus === st ? 'active' : ''}`}
                                style={{ fontSize: '12px', padding: '6px 14px' }}
                            >
                                {st}
                            </button>
                        ))}
                    </div>
                    <button onClick={fetchData} className="btn-secondary" style={{ padding: '6px 14px', fontSize: '12px', fontWeight: 600 }}>
                        ↻ Refresh Inventory
                    </button>
                </div>

                {loading ? (
                    <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-secondary, #70766F)' }}>Loading room statuses...</div>
                ) : filteredRooms.length === 0 ? (
                    <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-secondary, #70766F)' }}>No rooms match the selected filter.</div>
                ) : (
                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', textAlign: 'left' }}>
                            <thead>
                                <tr style={{ backgroundColor: 'var(--surface-muted, #F0F1EC)', borderBottom: '1px solid var(--border-color, #E5E7E1)', color: 'var(--text-secondary, #70766F)' }}>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Room #</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Property</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Suite Type</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Current Status</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Available for Booking?</th>
                                    <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Staff Assigned</th>
                                    <th style={{ padding: '12px 18px', textAlign: 'right', fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Update Status Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredRooms.map(r => (
                                    <tr key={r.id} style={{ borderBottom: '1px solid var(--border-color, #E5E7E1)', transition: 'background-color 0.15s ease' }}>
                                        <td style={{ padding: '14px 18px', fontWeight: 700, color: 'var(--text-primary, #252925)' }}>Room {r.roomNumber}</td>
                                        <td style={{ padding: '14px 18px', color: 'var(--text-secondary, #70766F)', fontSize: '12px' }}>{r.hotelId}</td>
                                        <td style={{ padding: '14px 18px', color: 'var(--text-primary, #252925)', fontWeight: 600 }}>{r.type} Suite</td>
                                        <td style={{ padding: '14px 18px' }}>
                                            <span className={`badge badge-${r.housekeepingStatus?.toLowerCase() || 'clean'}`}>
                                                {r.housekeepingStatus}
                                            </span>
                                        </td>
                                        <td style={{ padding: '14px 18px' }}>
                                            {(r.isAvailable ?? r.available ?? (r.housekeepingStatus === 'CLEAN' || r.housekeepingStatus === 'INSPECTED')) ? (
                                                <span style={{ color: '#15803d', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>✓ Ready</span>
                                            ) : (
                                                <span style={{ color: '#b91c1c', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>✗ Blocked</span>
                                            )}
                                        </td>
                                        <td style={{ padding: '14px 18px' }}>
                                            <input 
                                                type="text" 
                                                placeholder="Staff name"
                                                defaultValue={r.assignedStaff || ''}
                                                onChange={(e) => setStaffNames({ ...staffNames, [r.id]: e.target.value })}
                                                style={{ 
                                                    padding: '6px 10px', 
                                                    fontSize: '12px', 
                                                    width: '140px',
                                                    borderRadius: '6px',
                                                    border: '1px solid var(--border-color, #E5E7E1)',
                                                    backgroundColor: '#FFFFFF',
                                                    color: 'var(--text-primary, #252925)'
                                                }}
                                            />
                                        </td>
                                        <td style={{ padding: '14px 18px', textAlign: 'right' }}>
                                            <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                                                {r.housekeepingStatus !== 'CLEANING' && (
                                                    <button 
                                                        onClick={() => handleStatusChange(r.id, 'CLEANING')}
                                                        style={{ padding: '6px 12px', fontSize: '11px', fontWeight: 700, backgroundColor: 'rgba(197, 164, 109, 0.15)', color: '#8c6b2d', border: '1px solid rgba(197, 164, 109, 0.3)', borderRadius: '6px', cursor: 'pointer', transition: 'all 0.15s ease' }}
                                                    >
                                                        🧹 Clean
                                                    </button>
                                                )}
                                                {r.housekeepingStatus !== 'CLEAN' && (
                                                    <button 
                                                        onClick={() => handleStatusChange(r.id, 'CLEAN')}
                                                        style={{ padding: '6px 12px', fontSize: '11px', fontWeight: 700, backgroundColor: 'rgba(23, 74, 58, 0.1)', color: 'var(--primary-brand, #174A3A)', border: '1px solid rgba(23, 74, 58, 0.25)', borderRadius: '6px', cursor: 'pointer', transition: 'all 0.15s ease' }}
                                                    >
                                                        ✓ Ready
                                                    </button>
                                                )}
                                                {r.housekeepingStatus !== 'INSPECTED' && (
                                                    <button 
                                                        onClick={() => handleStatusChange(r.id, 'INSPECTED')}
                                                        style={{ padding: '6px 12px', fontSize: '11px', fontWeight: 700, backgroundColor: '#eff6ff', color: '#1e40af', border: '1px solid #bfdbfe', borderRadius: '6px', cursor: 'pointer', transition: 'all 0.15s ease' }}
                                                    >
                                                        🔍 Inspect
                                                    </button>
                                                )}
                                                {r.housekeepingStatus !== 'DIRTY' && (
                                                    <button 
                                                        onClick={() => handleStatusChange(r.id, 'DIRTY')}
                                                        style={{ padding: '6px 12px', fontSize: '11px', fontWeight: 700, backgroundColor: '#fee2e2', color: '#991b1b', border: '1px solid #fecaca', borderRadius: '6px', cursor: 'pointer', transition: 'all 0.15s ease' }}
                                                    >
                                                        ⚠️ Dirty
                                                    </button>
                                                )}
                                            </div>
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

export default HousekeepingDashboard;
