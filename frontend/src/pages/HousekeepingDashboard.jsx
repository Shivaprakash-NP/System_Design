import React, { useState, useEffect } from 'react';
import api from '../services/api';

const HousekeepingDashboard = () => {
    const [rooms, setRooms] = useState([]);
    const [summary, setSummary] = useState({});
    const [loading, setLoading] = useState(true);
    const [filterStatus, setFilterStatus] = useState('ALL');
    const [staffNames, setStaffNames] = useState({});

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
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    const handleStatusChange = async (roomId, newStatus) => {
        try {
            const assignedStaff = staffNames[roomId] || 'Assigned Staff';
            await api.put(`/housekeeping/${roomId}/status`, {
                status: newStatus,
                assignedStaff
            });
            fetchData();
        } catch (err) {
            alert(err.response?.data?.error || "Status update failed.");
        }
    };

    const filteredRooms = filterStatus === 'ALL'
        ? rooms
        : rooms.filter(r => r.housekeepingStatus === filterStatus);

    return (
        <div style={{ maxWidth: '1100px', margin: '0 auto', padding: '16px' }}>
            {/* Header */}
            <div style={{ backgroundColor: '#ffffff', padding: '24px', borderRadius: '12px', border: '1px solid #e2e8f0', marginBottom: '24px' }}>
                <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#0f172a' }}>Housekeeping & Operational Status</h1>
                <p style={{ fontSize: '13px', color: '#64748b' }}>Manage room turnover, sanitization workflows, staff allocations, and inspection certifications.</p>

                {/* KPI Cards */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px', marginTop: '20px' }}>
                    <div style={{ padding: '14px', borderRadius: '8px', backgroundColor: '#f8fafc', border: '1px solid #e2e8f0' }}>
                        <span style={{ fontSize: '12px', color: '#64748b', fontWeight: 600 }}>TOTAL INVENTORY</span>
                        <div style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', marginTop: '4px' }}>{rooms.length} Rooms</div>
                    </div>
                    <div style={{ padding: '14px', borderRadius: '8px', backgroundColor: '#dcfce7', border: '1px solid #bbf7d0' }}>
                        <span style={{ fontSize: '12px', color: '#166534', fontWeight: 600 }}>CLEAN & READY</span>
                        <div style={{ fontSize: '24px', fontWeight: 700, color: '#15803d', marginTop: '4px' }}>{summary['CLEAN'] || 0}</div>
                    </div>
                    <div style={{ padding: '14px', borderRadius: '8px', backgroundColor: '#fee2e2', border: '1px solid #fecaca' }}>
                        <span style={{ fontSize: '12px', color: '#991b1b', fontWeight: 600 }}>DIRTY (TURNOVER)</span>
                        <div style={{ fontSize: '24px', fontWeight: 700, color: '#b91c1c', marginTop: '4px' }}>{summary['DIRTY'] || 0}</div>
                    </div>
                    <div style={{ padding: '14px', borderRadius: '8px', backgroundColor: '#fef3c7', border: '1px solid #fde68a' }}>
                        <span style={{ fontSize: '12px', color: '#92400e', fontWeight: 600 }}>IN PROGRESS</span>
                        <div style={{ fontSize: '24px', fontWeight: 700, color: '#b45309', marginTop: '4px' }}>{summary['CLEANING'] || 0}</div>
                    </div>
                    <div style={{ padding: '14px', borderRadius: '8px', backgroundColor: '#e0f2fe', border: '1px solid #bae6fd' }}>
                        <span style={{ fontSize: '12px', color: '#075985', fontWeight: 600 }}>INSPECTED</span>
                        <div style={{ fontSize: '24px', fontWeight: 700, color: '#0369a1', marginTop: '4px' }}>{summary['INSPECTED'] || 0}</div>
                    </div>
                </div>
            </div>

            {/* Room List Table */}
            <div style={{ backgroundColor: '#ffffff', borderRadius: '12px', border: '1px solid #e2e8f0', overflow: 'hidden' }}>
                <div style={{ padding: '16px 20px', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ display: 'flex', gap: '8px' }}>
                        {['ALL', 'DIRTY', 'CLEANING', 'CLEAN', 'INSPECTED'].map(st => (
                            <button
                                key={st}
                                onClick={() => setFilterStatus(st)}
                                style={{
                                    padding: '6px 12px',
                                    borderRadius: '6px',
                                    fontSize: '12px',
                                    backgroundColor: filterStatus === st ? '#0f172a' : '#f1f5f9',
                                    color: filterStatus === st ? '#ffffff' : '#475569',
                                    fontWeight: filterStatus === st ? 600 : 500
                                }}
                            >
                                {st}
                            </button>
                        ))}
                    </div>
                    <button onClick={fetchData} className="btn-secondary" style={{ padding: '4px 10px', fontSize: '12px' }}>
                        ↻ Refresh
                    </button>
                </div>

                {loading ? (
                    <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>Loading room statuses...</div>
                ) : filteredRooms.length === 0 ? (
                    <div style={{ padding: '40px', textAlign: 'center', color: '#94a3b8' }}>No rooms match the selected filter.</div>
                ) : (
                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', textAlign: 'left' }}>
                            <thead>
                                <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569' }}>
                                    <th style={{ padding: '12px 16px' }}>Room #</th>
                                    <th style={{ padding: '12px 16px' }}>Hotel ID</th>
                                    <th style={{ padding: '12px 16px' }}>Type</th>
                                    <th style={{ padding: '12px 16px' }}>Current Status</th>
                                    <th style={{ padding: '12px 16px' }}>Available for Booking?</th>
                                    <th style={{ padding: '12px 16px' }}>Staff Assigned</th>
                                    <th style={{ padding: '12px 16px', textAlign: 'right' }}>Update Status Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredRooms.map(r => (
                                    <tr key={r.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                                        <td style={{ padding: '14px 16px', fontWeight: 700, color: '#0f172a' }}>Room {r.roomNumber}</td>
                                        <td style={{ padding: '14px 16px', color: '#64748b' }}>{r.hotelId}</td>
                                        <td style={{ padding: '14px 16px' }}>{r.type}</td>
                                        <td style={{ padding: '14px 16px' }}>
                                            <span className={`badge badge-${r.housekeepingStatus?.toLowerCase() || 'clean'}`}>
                                                {r.housekeepingStatus}
                                            </span>
                                        </td>
                                        <td style={{ padding: '14px 16px' }}>
                                            {r.isAvailable ? (
                                                <span style={{ color: '#15803d', fontWeight: 600 }}>✓ Ready</span>
                                            ) : (
                                                <span style={{ color: '#b91c1c', fontWeight: 600 }}>✗ Blocked</span>
                                            )}
                                        </td>
                                        <td style={{ padding: '14px 16px' }}>
                                            <input 
                                                type="text" 
                                                placeholder="Staff name"
                                                defaultValue={r.assignedStaff || ''}
                                                onChange={(e) => setStaffNames({ ...staffNames, [r.id]: e.target.value })}
                                                style={{ padding: '4px 8px', fontSize: '12px', width: '130px' }}
                                            />
                                        </td>
                                        <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                                            <div style={{ display: 'flex', gap: '4px', justifyContent: 'flex-end' }}>
                                                {r.housekeepingStatus !== 'CLEANING' && (
                                                    <button 
                                                        onClick={() => handleStatusChange(r.id, 'CLEANING')}
                                                        style={{ padding: '4px 8px', fontSize: '11px', backgroundColor: '#fef3c7', color: '#b45309', borderRadius: '4px' }}
                                                    >
                                                        Start Cleaning
                                                    </button>
                                                )}
                                                {r.housekeepingStatus !== 'CLEAN' && (
                                                    <button 
                                                        onClick={() => handleStatusChange(r.id, 'CLEAN')}
                                                        style={{ padding: '4px 8px', fontSize: '11px', backgroundColor: '#dcfce7', color: '#15803d', borderRadius: '4px' }}
                                                    >
                                                        Mark Clean
                                                    </button>
                                                )}
                                                {r.housekeepingStatus !== 'INSPECTED' && (
                                                    <button 
                                                        onClick={() => handleStatusChange(r.id, 'INSPECTED')}
                                                        style={{ padding: '4px 8px', fontSize: '11px', backgroundColor: '#e0f2fe', color: '#0369a1', borderRadius: '4px' }}
                                                    >
                                                        Certify Inspected
                                                    </button>
                                                )}
                                                {r.housekeepingStatus !== 'DIRTY' && (
                                                    <button 
                                                        onClick={() => handleStatusChange(r.id, 'DIRTY')}
                                                        style={{ padding: '4px 8px', fontSize: '11px', backgroundColor: '#fee2e2', color: '#b91c1c', borderRadius: '4px' }}
                                                    >
                                                        Flag Dirty
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
