import React, { useState, useEffect, useContext, useRef } from 'react';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';

const playNotificationSound = () => {
    try {
        const AudioCtx = window.AudioContext || window.webkitAudioContext;
        if (!AudioCtx) return;
        const ctx = new AudioCtx();
        if (ctx.state === 'suspended') {
            ctx.resume();
        }

        const now = ctx.currentTime;
        
        // Chime Note 1 (E5 - 659.25Hz)
        const osc1 = ctx.createOscillator();
        const gain1 = ctx.createGain();
        osc1.type = 'sine';
        osc1.frequency.setValueAtTime(659.25, now);
        gain1.gain.setValueAtTime(0.25, now);
        gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.3);
        osc1.connect(gain1);
        gain1.connect(ctx.destination);
        osc1.start(now);
        osc1.stop(now + 0.3);

        // Chime Note 2 (B5 - 987.77Hz)
        const osc2 = ctx.createOscillator();
        const gain2 = ctx.createGain();
        osc2.type = 'sine';
        osc2.frequency.setValueAtTime(987.77, now + 0.12);
        gain2.gain.setValueAtTime(0.3, now + 0.12);
        gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.5);
        osc2.connect(gain2);
        gain2.connect(ctx.destination);
        osc2.start(now + 0.12);
        osc2.stop(now + 0.5);
    } catch (err) {
        console.warn("Could not play notification chime:", err);
    }
};

const NotificationCenter = () => {
    const { isAuthenticated } = useContext(AuthContext);
    const [notifications, setNotifications] = useState([]);
    const [isOpen, setIsOpen] = useState(false);
    const [unreadCount, setUnreadCount] = useState(0);
    const [soundEnabled, setSoundEnabled] = useState(() => localStorage.getItem('notification_sound') !== 'false');
    const dropdownRef = useRef(null);
    const isFirstLoadRef = useRef(true);
    const seenIdsRef = useRef(new Set());

    const isUnread = (n) => !n.read && !n.isRead;

    const toggleSound = (e) => {
        if (e) e.stopPropagation();
        const next = !soundEnabled;
        setSoundEnabled(next);
        localStorage.setItem('notification_sound', String(next));
        if (next) {
            playNotificationSound();
        }
    };

    const fetchNotifications = async () => {
        if (!isAuthenticated) return;
        try {
            const res = await api.get('/notifications/my');
            const data = Array.isArray(res.data) ? res.data : [];
            
            if (isFirstLoadRef.current) {
                data.forEach(n => seenIdsRef.current.add(n.id));
                isFirstLoadRef.current = false;
            } else {
                const hasNewUnread = data.some(n => !seenIdsRef.current.has(n.id) && isUnread(n));
                if (hasNewUnread && soundEnabled) {
                    playNotificationSound();
                }
                data.forEach(n => seenIdsRef.current.add(n.id));
            }

            setNotifications(data);
            setUnreadCount(data.filter(isUnread).length);
        } catch (err) {
            console.error("Failed to load notifications", err);
        }
    };

    useEffect(() => {
        if (isAuthenticated) {
            fetchNotifications();
            const interval = setInterval(fetchNotifications, 5000);
            return () => clearInterval(interval);
        }
    }, [isAuthenticated]);

    // Handle outside click to close dropdown
    useEffect(() => {
        const handleClickOutside = (event) => {
            if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
                setIsOpen(false);
            }
        };
        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
        }
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, [isOpen]);

    const markAsRead = async (id, e) => {
        if (e) e.stopPropagation();
        try {
            await api.put(`/notifications/${id}/read`);
            setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true, isRead: true } : n));
            setUnreadCount(prev => Math.max(0, prev - 1));
        } catch (err) {
            console.error(err);
        }
    };

    const markAllRead = async () => {
        try {
            await api.put('/notifications/mark-all-read');
            setNotifications(prev => prev.map(n => ({ ...n, read: true, isRead: true })));
            setUnreadCount(0);
        } catch (err) {
            console.error(err);
        }
    };

    if (!isAuthenticated) return null;

    return (
        <div style={{ position: 'relative' }} ref={dropdownRef}>
            <button 
                onClick={() => setIsOpen(!isOpen)}
                style={{
                    background: 'rgba(255, 255, 255, 0.12)',
                    border: '1px solid rgba(255, 255, 255, 0.22)',
                    borderRadius: '8px',
                    position: 'relative',
                    padding: '7px 10px',
                    color: '#ffffff',
                    fontSize: '16px',
                    display: 'flex',
                    alignItems: 'center',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease'
                }}
                title="Notifications"
            >
                🔔
                {unreadCount > 0 && (
                    <span style={{
                        position: 'absolute',
                        top: '-4px',
                        right: '-4px',
                        background: '#C5A46D',
                        color: '#174A3A',
                        borderRadius: '10px',
                        padding: '1px 6px',
                        fontSize: '10px',
                        fontWeight: 800,
                        border: '2px solid #174A3A',
                        boxShadow: '0 2px 4px rgba(0,0,0,0.2)'
                    }}>
                        {unreadCount > 9 ? '9+' : unreadCount}
                    </span>
                )}
            </button>

            {isOpen && (
                <div style={{
                    position: 'absolute',
                    right: 0,
                    top: '46px',
                    width: '360px',
                    maxHeight: '440px',
                    overflowY: 'auto',
                    backgroundColor: '#FFFFFF',
                    borderRadius: '12px',
                    boxShadow: '0 16px 40px rgba(23, 74, 58, 0.15)',
                    border: '1px solid #E5E7E1',
                    zIndex: 1100,
                    padding: '16px'
                }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px', borderBottom: '1px solid #F0F1EC', paddingBottom: '10px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <span style={{ fontWeight: 700, fontSize: '14px', color: '#174A3A' }}>Stay Notifications</span>
                            {unreadCount > 0 && (
                                <span style={{ fontSize: '10px', backgroundColor: '#EBF3EF', color: '#174A3A', padding: '2px 8px', borderRadius: '12px', fontWeight: 700, border: '1px solid #D1E5DC' }}>
                                    {unreadCount} unread
                                </span>
                            )}
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <button
                                type="button"
                                onClick={toggleSound}
                                title={soundEnabled ? "Notification chime enabled (Click to mute)" : "Notification chime muted (Click to enable)"}
                                style={{
                                    fontSize: '11px',
                                    padding: '3px 8px',
                                    borderRadius: '6px',
                                    border: '1px solid #E5E7E1',
                                    backgroundColor: soundEnabled ? '#F0FDF4' : '#F0F1EC',
                                    color: soundEnabled ? '#15803D' : '#70766F',
                                    cursor: 'pointer',
                                    fontWeight: 600,
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '4px'
                                }}
                            >
                                {soundEnabled ? "🔊 Sound On" : "🔇 Muted"}
                            </button>
                            {unreadCount > 0 && (
                                <button 
                                    onClick={markAllRead} 
                                    style={{ fontSize: '11px', color: '#174A3A', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 600, textDecoration: 'underline' }}
                                >
                                    Mark all read
                                </button>
                            )}
                        </div>
                    </div>

                    {notifications.length === 0 ? (
                        <div style={{ padding: '36px 12px', textAlign: 'center' }}>
                            <span style={{ fontSize: '24px', display: 'block', marginBottom: '6px' }}>🔕</span>
                            <p style={{ fontSize: '13px', color: '#252925', margin: 0, fontWeight: 600 }}>No notifications yet</p>
                            <p style={{ fontSize: '11.5px', color: '#70766F', margin: '4px 0 0 0' }}>Stay updates will arrive here in real time.</p>
                        </div>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                            {notifications.map(n => {
                                const unread = isUnread(n);
                                return (
                                    <div 
                                        key={n.id}
                                        onClick={() => unread && markAsRead(n.id)}
                                        style={{
                                            padding: '10px 14px',
                                            borderRadius: '8px',
                                            backgroundColor: unread ? '#FAF9F6' : '#FFFFFF',
                                            border: unread ? '1px solid #E5E7E1' : '1px solid #F0F1EC',
                                            cursor: unread ? 'pointer' : 'default',
                                            transition: 'all 0.15s ease'
                                        }}
                                    >
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                                {unread && (
                                                    <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#C5A46D', display: 'inline-block' }}></span>
                                                )}
                                                <span style={{ fontSize: '12.5px', fontWeight: 700, color: unread ? '#174A3A' : '#252925' }}>
                                                    {n.title}
                                                </span>
                                            </div>
                                            <span style={{ fontSize: '10px', color: '#70766F', whiteSpace: 'nowrap' }}>
                                                {n.createdAt ? new Date(n.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                                            </span>
                                        </div>
                                        <p style={{ fontSize: '11.5px', color: '#70766F', marginTop: '4px', lineHeight: 1.45, margin: '4px 0 0 0' }}>
                                            {n.message}
                                        </p>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default NotificationCenter;
