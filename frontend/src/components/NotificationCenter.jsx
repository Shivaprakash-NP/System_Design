import React, { useState, useEffect, useContext } from 'react';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';

const NotificationCenter = () => {
    const { isAuthenticated, user } = useContext(AuthContext);
    const [notifications, setNotifications] = useState([]);
    const [isOpen, setIsOpen] = useState(false);
    const [unreadCount, setUnreadCount] = useState(0);

    const fetchNotifications = async () => {
        if (!isAuthenticated) return;
        try {
            const res = await api.get('/notifications/my');
            setNotifications(res.data);
            setUnreadCount(res.data.filter(n => !n.read).length);
        } catch (err) {
            console.error("Failed to load notifications", err);
        }
    };

    useEffect(() => {
        if (isAuthenticated) {
            fetchNotifications();
            const interval = setInterval(fetchNotifications, 10000);
            return () => clearInterval(interval);
        }
    }, [isAuthenticated]);

    const markAsRead = async (id) => {
        try {
            await api.put(`/notifications/${id}/read`);
            fetchNotifications();
        } catch (err) {
            console.error(err);
        }
    };

    const markAllRead = async () => {
        try {
            await api.put('/notifications/mark-all-read');
            fetchNotifications();
        } catch (err) {
            console.error(err);
        }
    };

    if (!isAuthenticated) return null;

    return (
        <div style={{ position: 'relative' }}>
            <button 
                onClick={() => setIsOpen(!isOpen)}
                style={{
                    background: 'transparent',
                    border: 'none',
                    position: 'relative',
                    padding: '8px',
                    color: '#ffffff',
                    fontSize: '18px',
                    display: 'flex',
                    alignItems: 'center'
                }}
                title="Notifications"
            >
                🔔
                {unreadCount > 0 && (
                    <span style={{
                        position: 'absolute',
                        top: '2px',
                        right: '2px',
                        background: '#ef4444',
                        color: '#ffffff',
                        borderRadius: '50%',
                        padding: '2px 6px',
                        fontSize: '10px',
                        fontWeight: 'bold'
                    }}>
                        {unreadCount}
                    </span>
                )}
            </button>

            {isOpen && (
                <div style={{
                    position: 'absolute',
                    right: 0,
                    top: '40px',
                    width: '320px',
                    maxHeight: '400px',
                    overflowY: 'auto',
                    backgroundColor: '#ffffff',
                    borderRadius: '8px',
                    boxShadow: '0 10px 25px rgba(0,0,0,0.15)',
                    border: '1px solid #e2e8f0',
                    zIndex: 1000,
                    padding: '12px'
                }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', borderBottom: '1px solid #f1f5f9', paddingBottom: '6px' }}>
                        <span style={{ fontWeight: 600, fontSize: '14px', color: '#0f172a' }}>Notifications</span>
                        {unreadCount > 0 && (
                            <button onClick={markAllRead} style={{ fontSize: '11px', color: '#0d9488', background: 'none', border: 'none' }}>
                                Mark all as read
                            </button>
                        )}
                    </div>

                    {notifications.length === 0 ? (
                        <p style={{ fontSize: '13px', color: '#94a3b8', textAlign: 'center', margin: '20px 0' }}>No notifications yet</p>
                    ) : (
                        notifications.map(n => (
                            <div 
                                key={n.id}
                                onClick={() => !n.read && markAsRead(n.id)}
                                style={{
                                    padding: '8px',
                                    borderRadius: '6px',
                                    marginBottom: '6px',
                                    backgroundColor: n.read ? '#ffffff' : '#f0fdfa',
                                    border: n.read ? '1px solid #f1f5f9' : '1px solid #ccfbf1',
                                    cursor: n.read ? 'default' : 'pointer'
                                }}
                            >
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <span style={{ fontSize: '12px', fontWeight: 600, color: '#0f172a' }}>{n.title}</span>
                                    <span style={{ fontSize: '10px', color: '#94a3b8' }}>
                                        {n.createdAt ? new Date(n.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                                    </span>
                                </div>
                                <p style={{ fontSize: '11px', color: '#475569', marginTop: '2px', lineHeight: 1.3 }}>{n.message}</p>
                            </div>
                        ))
                    )}
                </div>
            )}
        </div>
    );
};

export default NotificationCenter;
