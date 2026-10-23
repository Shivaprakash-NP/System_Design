import React, { useState, useEffect, useContext } from 'react';
import api from '../services/api';
import SearchBarWithAutocomplete from '../components/SearchBarWithAutocomplete';
import { AuthContext } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

const RoomSearch = () => {
    const { isAuthenticated, user } = useContext(AuthContext);
    const navigate = useNavigate();

    const [hotels, setHotels] = useState([]);
    const [selectedHotel, setSelectedHotel] = useState(null);
    const [rooms, setRooms] = useState([]);
    const [roomSortBy, setRoomSortBy] = useState('price-asc');
    const [roomTypeFilter, setRoomTypeFilter] = useState('ALL');
    const [cityFilter, setCityFilter] = useState('');
    const [searchQuery, setSearchQuery] = useState('');

    // Booking Dates (defaults: today + 1 to today + 3)
    const today = new Date().toISOString().split('T')[0];
    const defaultCheckOut = new Date(Date.now() + 2 * 86400000).toISOString().split('T')[0];
    const [checkInDate, setCheckInDate] = useState(today);
    const [checkOutDate, setCheckOutDate] = useState(defaultCheckOut);

    // Booking Modal
    const [selectedRoom, setSelectedRoom] = useState(null);
    const [bookingLoading, setBookingLoading] = useState(false);
    const [bookingSuccess, setBookingSuccess] = useState(null);
    const [bookingError, setBookingError] = useState('');

    // Fetch Hotels
    const fetchHotels = async () => {
        try {
            const params = new URLSearchParams();
            if (cityFilter) params.append('city', cityFilter);
            if (searchQuery) params.append('query', searchQuery);
            const res = await api.get(`/hotels?${params.toString()}`);
            setHotels(res.data);
            if (res.data.length > 0 && !selectedHotel) {
                setSelectedHotel(res.data[0]);
            }
        } catch (err) {
            console.error("Error fetching hotels", err);
        }
    };

    useEffect(() => {
        fetchHotels();
    }, [cityFilter, searchQuery]);

    // Fetch Rooms for selected hotel & dates
    const fetchRooms = async () => {
        if (!selectedHotel) return;
        try {
            const res = await api.get(`/rooms/available-dates`, {
                params: {
                    hotelId: selectedHotel.id,
                    checkIn: checkInDate,
                    checkOut: checkOutDate,
                    sortBy: roomSortBy
                }
            });
            setRooms(res.data);
        } catch (err) {
            // Fallback to general available rooms
            try {
                const res2 = await api.get(`/rooms/available/${selectedHotel.id}`, {
                    params: { sortBy: roomSortBy }
                });
                setRooms(res2.data);
            } catch (e) {
                console.error(e);
            }
        }
    };

    useEffect(() => {
        if (selectedHotel) {
            fetchRooms();
        }
    }, [selectedHotel, checkInDate, checkOutDate, roomSortBy]);

    // Client-side instant sorting and filtering
    const sortedAndFilteredRooms = React.useMemo(() => {
        let list = [...rooms];
        if (roomTypeFilter !== 'ALL') {
            list = list.filter(r => r.type?.toUpperCase() === roomTypeFilter.toUpperCase());
        }
        switch (roomSortBy) {
            case 'price-asc':
                list.sort((a, b) => a.pricePerNight - b.pricePerNight);
                break;
            case 'price-desc':
                list.sort((a, b) => b.pricePerNight - a.pricePerNight);
                break;
            case 'capacity-desc':
                list.sort((a, b) => (b.capacity || 2) - (a.capacity || 2));
                break;
            case 'capacity-asc':
                list.sort((a, b) => (a.capacity || 2) - (b.capacity || 2));
                break;
            case 'roomNumber-asc':
                list.sort((a, b) => {
                    const numA = parseInt(a.roomNumber, 10);
                    const numB = parseInt(b.roomNumber, 10);
                    return (!isNaN(numA) && !isNaN(numB)) ? numA - numB : String(a.roomNumber).localeCompare(String(b.roomNumber));
                });
                break;
            default:
                break;
        }
        return list;
    }, [rooms, roomSortBy, roomTypeFilter]);

    // Calculate nights & estimated totals
    const calculateNights = () => {
        const d1 = new Date(checkInDate);
        const d2 = new Date(checkOutDate);
        const diff = Math.ceil((d2 - d1) / (1000 * 60 * 60 * 24));
        return diff > 0 ? diff : 1;
    };

    const handleConfirmBooking = async () => {
        if (!isAuthenticated) {
            navigate('/login');
            return;
        }

        setBookingLoading(true);
        setBookingError('');
        setBookingSuccess(null);

        const idempotencyKey = `idem_${user?.email || 'guest'}_${selectedRoom.id}_${checkInDate}_${checkOutDate}_${Date.now()}`;

        try {
            const res = await api.post('/bookings', {
                hotelId: selectedHotel.id,
                roomId: selectedRoom.id,
                checkInDate,
                checkOutDate,
                guestName: user?.fullName || 'Valued Guest',
                guestEmail: user?.email || 'guest@hotel.demo',
                idempotencyKey
            });

            setBookingSuccess(res.data);
            fetchRooms(); // Refresh availability
        } catch (err) {
            if (err.response?.status === 429) {
                setBookingError("⚠️ Rate limit exceeded. Token bucket depleted. Please wait a moment.");
            } else if (err.response?.status === 409) {
                setBookingError("⚠️ Room is already booked for these selected dates. Please choose different dates or another room.");
            } else {
                setBookingError(err.response?.data?.error || "Booking failed. Please try again.");
            }
        } finally {
            setBookingLoading(false);
        }
    };

    // Fallback photography by room type
    const getRoomPhoto = (type) => {
        const t = (type || '').toUpperCase();
        if (t.includes('SUITE')) return 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=600&q=80';
        if (t.includes('DELUXE')) return 'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=600&q=80';
        if (t.includes('DOUBLE')) return 'https://images.unsplash.com/photo-1566665797739-1674de7a421a?auto=format&fit=crop&w=600&q=80';
        return 'https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=600&q=80';
    };

    return (
        <div style={{ maxWidth: '1280px', margin: '0 auto', padding: '24px 20px 48px' }}>
            {/* Editorial Hero & Search Bar */}
            <div style={{ 
                backgroundColor: 'var(--surface, #ffffff)', 
                padding: '28px 32px', 
                borderRadius: '16px', 
                border: '1px solid var(--border-color, #E5E7E1)', 
                marginBottom: '28px', 
                boxShadow: 'var(--shadow-sm)'
            }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px', marginBottom: '20px' }}>
                    <div>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', padding: '4px 10px', borderRadius: '100px', backgroundColor: 'var(--surface-muted, #F0F1EC)', color: 'var(--primary-brand, #174A3A)', fontSize: '11px', fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase', marginBottom: '10px' }}>
                            <span>✦</span> Curated Accommodations
                        </div>
                        <h1 style={{ fontFamily: 'Playfair Display, Cinzel, serif', fontSize: '28px', fontWeight: 700, color: 'var(--text-primary, #252925)', margin: 0, letterSpacing: '-0.02em' }}>
                            Find Your Next Stay
                        </h1>
                        <p style={{ fontSize: '14px', color: 'var(--text-secondary, #70766F)', margin: '6px 0 0', fontWeight: 400 }}>
                            Search low prices on luxury suites, boutique properties, and premier accommodations across India
                        </p>
                    </div>

                    <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '6px 12px', borderRadius: '8px', backgroundColor: 'rgba(197, 164, 109, 0.12)', border: '1px solid rgba(197, 164, 109, 0.3)', color: '#8c6b2d', fontSize: '12px', fontWeight: 600 }}>
                        <span>⚡</span> Best Rates with 12% GST Clarity
                    </div>
                </div>

                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '14px', alignItems: 'center' }}>
                    <SearchBarWithAutocomplete onSelect={(term) => setSearchQuery(term)} />

                    <select 
                        value={cityFilter} 
                        onChange={(e) => setCityFilter(e.target.value)}
                        style={{ 
                            padding: '10px 14px', 
                            fontSize: '13px', 
                            minWidth: '150px',
                            borderRadius: '8px',
                            border: '1px solid var(--border-color, #E5E7E1)',
                            backgroundColor: '#FFFFFF',
                            color: 'var(--text-primary, #252925)',
                            fontWeight: 500
                        }}
                    >
                        <option value="">All Destinations</option>
                        <option value="Chennai">Chennai</option>
                        <option value="Mumbai">Mumbai</option>
                        <option value="Bengaluru">Bengaluru</option>
                    </select>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary, #70766F)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Check-In:</label>
                        <input 
                            type="date" 
                            value={checkInDate} 
                            min={today}
                            onChange={(e) => setCheckInDate(e.target.value)} 
                            style={{
                                padding: '9px 12px',
                                fontSize: '13px',
                                borderRadius: '8px',
                                border: '1px solid var(--border-color, #E5E7E1)',
                                backgroundColor: '#FFFFFF',
                                color: 'var(--text-primary, #252925)',
                                fontWeight: 500
                            }}
                        />
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary, #70766F)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Check-Out:</label>
                        <input 
                            type="date" 
                            value={checkOutDate} 
                            min={checkInDate}
                            onChange={(e) => setCheckOutDate(e.target.value)} 
                            style={{
                                padding: '9px 12px',
                                fontSize: '13px',
                                borderRadius: '8px',
                                border: '1px solid var(--border-color, #E5E7E1)',
                                backgroundColor: '#FFFFFF',
                                color: 'var(--text-primary, #252925)',
                                fontWeight: 500
                            }}
                        />
                    </div>
                </div>
            </div>

            {/* Hotel Cards Carousel / Grid */}
            <div style={{ marginBottom: '32px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: '14px' }}>
                    <h2 style={{ fontFamily: 'Playfair Display, serif', fontSize: '20px', fontWeight: 700, color: 'var(--text-primary, #252925)', margin: 0 }}>
                        Featured Hotels ({hotels.length})
                    </h2>
                    <span style={{ fontSize: '13px', color: 'var(--text-secondary, #70766F)' }}>
                        Select a property to view live suite availability
                    </span>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '18px' }}>
                    {hotels.map(h => {
                        const isSelected = selectedHotel?.id === h.id;
                        return (
                            <div 
                                key={h.id}
                                onClick={() => setSelectedHotel(h)}
                                style={{
                                    backgroundColor: 'var(--surface, #ffffff)',
                                    borderRadius: '12px',
                                    border: isSelected ? '2px solid var(--primary-brand, #174A3A)' : '1px solid var(--border-color, #E5E7E1)',
                                    overflow: 'hidden',
                                    cursor: 'pointer',
                                    transition: 'all 0.2s ease',
                                    boxShadow: isSelected ? '0 8px 24px rgba(23, 74, 58, 0.14)' : 'var(--shadow-sm)',
                                    transform: isSelected ? 'translateY(-2px)' : 'none'
                                }}
                            >
                                <div style={{ position: 'relative', width: '100%', height: '160px', overflow: 'hidden' }}>
                                    <img 
                                        src={h.imageUrl || 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=600&q=80'} 
                                        alt={h.name} 
                                        loading="lazy"
                                        style={{ width: '100%', height: '100%', objectFit: 'cover', transition: 'transform 0.3s ease' }} 
                                    />
                                    <span style={{ 
                                        position: 'absolute', 
                                        top: '10px', 
                                        right: '10px', 
                                        backgroundColor: 'rgba(23, 74, 58, 0.92)', 
                                        color: '#FFFFFF', 
                                        fontSize: '11px', 
                                        fontWeight: 700, 
                                        padding: '3px 8px', 
                                        borderRadius: '6px',
                                        letterSpacing: '0.02em',
                                        backdropFilter: 'blur(4px)'
                                    }}>
                                        ★ {h.rating || '4.8'}
                                    </span>
                                    {isSelected && (
                                        <span style={{
                                            position: 'absolute',
                                            bottom: '10px',
                                            left: '10px',
                                            backgroundColor: 'var(--accent-gold, #C5A46D)',
                                            color: '#1a1814',
                                            fontSize: '10px',
                                            fontWeight: 700,
                                            padding: '2px 8px',
                                            borderRadius: '4px',
                                            textTransform: 'uppercase',
                                            letterSpacing: '0.06em'
                                        }}>
                                            Active Selection
                                        </span>
                                    )}
                                </div>

                                <div style={{ padding: '16px' }}>
                                    <h3 style={{ fontSize: '16px', fontWeight: 700, color: 'var(--text-primary, #252925)', margin: 0, letterSpacing: '-0.01em' }}>
                                        {h.name}
                                    </h3>
                                    <p style={{ fontSize: '12px', color: 'var(--text-secondary, #70766F)', margin: '4px 0 10px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                                        <span style={{ color: 'var(--accent-gold, #C5A46D)' }}>📍</span> {h.city}
                                    </p>
                                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                                        {h.amenities?.slice(0, 3).map((a, i) => (
                                            <span key={i} style={{ 
                                                fontSize: '10px', 
                                                backgroundColor: 'var(--surface-muted, #F0F1EC)', 
                                                padding: '3px 7px', 
                                                borderRadius: '4px', 
                                                color: 'var(--text-secondary, #70766F)',
                                                fontWeight: 600
                                            }}>
                                                {a}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>

            {/* Room Availability Section */}
            {selectedHotel && (
                <div style={{ 
                    backgroundColor: 'var(--surface, #ffffff)', 
                    padding: '28px 32px', 
                    borderRadius: '16px', 
                    border: '1px solid var(--border-color, #E5E7E1)',
                    boxShadow: 'var(--shadow-sm)'
                }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '14px' }}>
                        <div>
                            <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '11px', fontWeight: 700, color: 'var(--primary-brand, #174A3A)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: '4px' }}>
                                Property Inventory
                            </div>
                            <h2 style={{ fontFamily: 'Playfair Display, serif', fontSize: '22px', fontWeight: 700, color: 'var(--text-primary, #252925)', margin: 0 }}>
                                Available Rooms at {selectedHotel.name}
                            </h2>
                            <p style={{ fontSize: '13px', color: 'var(--text-secondary, #70766F)', margin: '4px 0 0' }}>
                                Dates: <strong>{checkInDate}</strong> to <strong>{checkOutDate}</strong> ({calculateNights()} night{calculateNights() > 1 ? 's' : ''}) • {sortedAndFilteredRooms.length} room{sortedAndFilteredRooms.length !== 1 ? 's' : ''} available
                            </p>
                        </div>
                        <span style={{ 
                            fontSize: '12px', 
                            backgroundColor: 'rgba(23, 74, 58, 0.08)', 
                            color: 'var(--primary-brand, #174A3A)', 
                            border: '1px solid rgba(23, 74, 58, 0.18)', 
                            padding: '6px 12px', 
                            borderRadius: '8px', 
                            fontWeight: 600,
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '6px'
                        }}>
                            <span>⚡</span> Cached via Redis Key-Value Store
                        </span>
                    </div>

                    {/* Room Sorting & Filtering Toolbar */}
                    <div style={{ 
                        display: 'flex', 
                        justifyContent: 'space-between', 
                        alignItems: 'center', 
                        flexWrap: 'wrap', 
                        gap: '14px', 
                        padding: '12px 18px', 
                        backgroundColor: 'var(--surface-muted, #F0F1EC)', 
                        borderRadius: '10px', 
                        border: '1px solid var(--border-color, #E5E7E1)', 
                        marginBottom: '24px' 
                    }}>
                        {/* Quick Type Filter Chips */}
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                            <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-primary, #252925)', marginRight: '4px' }}>Filter:</span>
                            {['ALL', 'SINGLE', 'DOUBLE', 'DELUXE', 'SUITE'].map(type => (
                                <button
                                    key={type}
                                    type="button"
                                    onClick={() => setRoomTypeFilter(type)}
                                    className={`tab-pill ${roomTypeFilter === type ? 'active' : ''}`}
                                    style={{ fontSize: '12px', padding: '5px 12px' }}
                                >
                                    {type === 'ALL' ? 'All Types' : type}
                                </button>
                            ))}
                        </div>

                        {/* Room Sorting Options */}
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <label htmlFor="room-sort-select" style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-primary, #252925)' }}>
                                Sort By:
                            </label>
                            <select
                                id="room-sort-select"
                                value={roomSortBy}
                                onChange={(e) => setRoomSortBy(e.target.value)}
                                style={{
                                    padding: '7px 14px',
                                    fontSize: '12px',
                                    fontWeight: 600,
                                    borderRadius: '8px',
                                    border: '1px solid var(--border-color, #E5E7E1)',
                                    backgroundColor: '#ffffff',
                                    color: 'var(--text-primary, #252925)',
                                    cursor: 'pointer'
                                }}
                            >
                                <option value="price-asc">💰 Price: Low to High</option>
                                <option value="price-desc">💎 Price: High to Low</option>
                                <option value="capacity-desc">👥 Room Size / Capacity: Largest First</option>
                                <option value="capacity-asc">👤 Room Size / Capacity: Smallest First</option>
                                <option value="roomNumber-asc">🚪 Room Number: Ascending</option>
                            </select>
                        </div>
                    </div>

                    {sortedAndFilteredRooms.length === 0 ? (
                        <div style={{ textAlign: 'center', padding: '48px 20px', color: 'var(--text-secondary, #70766F)' }}>
                            <div style={{ fontSize: '32px', marginBottom: '8px' }}>🛎️</div>
                            <p style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary, #252925)' }}>No suites match the selected criteria.</p>
                            <p style={{ fontSize: '13px', marginTop: '4px' }}>Try switching the room type filter or selecting different dates.</p>
                        </div>
                    ) : (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '20px' }}>
                            {sortedAndFilteredRooms.map(room => (
                                <div 
                                    key={room.id}
                                    style={{
                                        border: '1px solid var(--border-color, #E5E7E1)',
                                        borderRadius: '12px',
                                        backgroundColor: '#ffffff',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        justifyContent: 'space-between',
                                        boxShadow: 'var(--shadow-sm)',
                                        overflow: 'hidden',
                                        transition: 'transform 0.2s ease, box-shadow 0.2s ease'
                                    }}
                                >
                                    {/* Room Card Thumbnail */}
                                    <div style={{ position: 'relative', height: '140px', width: '100%', overflow: 'hidden' }}>
                                        <img 
                                            src={getRoomPhoto(room.type)} 
                                            alt={`${room.type} Room ${room.roomNumber}`}
                                            loading="lazy"
                                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                        />
                                        <span style={{
                                            position: 'absolute',
                                            top: '10px',
                                            left: '10px',
                                            backgroundColor: 'rgba(23, 74, 58, 0.9)',
                                            color: '#FFFFFF',
                                            fontSize: '11px',
                                            fontWeight: 700,
                                            padding: '3px 8px',
                                            borderRadius: '6px',
                                            letterSpacing: '0.04em'
                                        }}>
                                            Room {room.roomNumber}
                                        </span>
                                        <span 
                                            className={`badge badge-${room.housekeepingStatus?.toLowerCase() || 'clean'}`}
                                            style={{ position: 'absolute', top: '10px', right: '10px' }}
                                        >
                                            {room.housekeepingStatus}
                                        </span>
                                    </div>

                                    <div style={{ padding: '16px 18px 12px' }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                                            <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--primary-brand, #174A3A)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                                                {room.type} SUITE
                                            </span>
                                            <span style={{ fontSize: '12px', color: 'var(--text-secondary, #70766F)', fontWeight: 500 }}>
                                                👥 Max {room.capacity || 1} Guest{(room.capacity || 1) > 1 ? 's' : ''}
                                            </span>
                                        </div>

                                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '5px', margin: '10px 0 4px' }}>
                                            {room.amenities?.map((am, idx) => (
                                                <span key={idx} style={{ 
                                                    fontSize: '11px', 
                                                    backgroundColor: 'var(--surface-muted, #F0F1EC)', 
                                                    border: '1px solid var(--border-color, #E5E7E1)', 
                                                    padding: '2px 7px', 
                                                    borderRadius: '4px', 
                                                    color: 'var(--text-secondary, #70766F)' 
                                                }}>
                                                    ✓ {am}
                                                </span>
                                            ))}
                                        </div>
                                    </div>

                                    <div style={{ 
                                        padding: '14px 18px', 
                                        backgroundColor: '#FAFAF8',
                                        borderTop: '1px solid var(--border-color, #E5E7E1)', 
                                        display: 'flex', 
                                        justifyContent: 'space-between', 
                                        alignItems: 'center' 
                                    }}>
                                        <div>
                                            <div style={{ display: 'flex', alignItems: 'baseline', gap: '3px' }}>
                                                <span style={{ fontSize: '20px', fontWeight: 800, color: 'var(--text-primary, #252925)' }}>
                                                    ${room.pricePerNight}
                                                </span>
                                                <span style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)' }}>/ night</span>
                                            </div>
                                            <span style={{ fontSize: '10px', color: 'var(--accent-gold, #C5A46D)', fontWeight: 600 }}>
                                                + 12% GST applicable
                                            </span>
                                        </div>

                                        <button 
                                            onClick={() => {
                                                setSelectedRoom(room);
                                                setBookingSuccess(null);
                                                setBookingError('');
                                            }}
                                            className="btn-primary"
                                            style={{ padding: '8px 16px', fontSize: '13px' }}
                                        >
                                            Reserve Room
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* Booking Modal */}
            {selectedRoom && (
                <div style={{
                    position: 'fixed',
                    top: 0, left: 0, right: 0, bottom: 0,
                    backgroundColor: 'rgba(23, 74, 58, 0.45)',
                    backdropFilter: 'blur(4px)',
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center',
                    zIndex: 1000,
                    padding: '16px'
                }}>
                    <div style={{
                        backgroundColor: '#ffffff',
                        borderRadius: '16px',
                        padding: '30px',
                        width: '100%',
                        maxWidth: '480px',
                        boxShadow: 'var(--shadow-xl)',
                        border: '1px solid var(--border-color, #E5E7E1)'
                    }}>
                        {!bookingSuccess ? (
                            <>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '18px' }}>
                                    <div>
                                        <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--accent-gold, #C5A46D)', letterSpacing: '0.08em', textTransform: 'uppercase' }}>
                                            Reservation Checkout
                                        </div>
                                        <h3 style={{ fontFamily: 'Playfair Display, serif', fontSize: '20px', fontWeight: 700, color: 'var(--text-primary, #252925)', margin: '2px 0 0' }}>
                                            Confirm Stay Reservation
                                        </h3>
                                    </div>
                                    <button 
                                        onClick={() => setSelectedRoom(null)} 
                                        style={{ 
                                            background: 'var(--surface-muted, #F0F1EC)', 
                                            border: 'none', 
                                            width: '32px',
                                            height: '32px',
                                            borderRadius: '50%',
                                            fontSize: '14px', 
                                            color: 'var(--text-secondary, #70766F)',
                                            cursor: 'pointer',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center'
                                        }}
                                    >
                                        ✕
                                    </button>
                                </div>

                                <div style={{ backgroundColor: 'var(--surface-muted, #F0F1EC)', padding: '16px', borderRadius: '10px', marginBottom: '18px', fontSize: '13px', border: '1px solid var(--border-color, #E5E7E1)' }}>
                                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                                        <div>
                                            <span style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)', display: 'block' }}>HOTEL PROPERTY</span>
                                            <strong style={{ color: 'var(--text-primary, #252925)' }}>{selectedHotel?.name}</strong>
                                        </div>
                                        <div>
                                            <span style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)', display: 'block' }}>ROOM SUITE</span>
                                            <strong style={{ color: 'var(--text-primary, #252925)' }}>#{selectedRoom.roomNumber} ({selectedRoom.type})</strong>
                                        </div>
                                        <div style={{ gridColumn: 'span 2', marginTop: '4px' }}>
                                            <span style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)', display: 'block' }}>DATES OF STAY</span>
                                            <strong style={{ color: 'var(--text-primary, #252925)' }}>{checkInDate} to {checkOutDate} ({calculateNights()} night{calculateNights() > 1 ? 's' : ''})</strong>
                                        </div>
                                        <div style={{ gridColumn: 'span 2' }}>
                                            <span style={{ fontSize: '11px', color: 'var(--text-secondary, #70766F)', display: 'block' }}>REGISTERED GUEST</span>
                                            <span style={{ color: 'var(--text-primary, #252925)' }}>{user?.fullName || 'John Guest'} ({user?.email || 'guest@hotel.demo'})</span>
                                        </div>
                                    </div>
                                </div>

                                {/* Price Breakdown */}
                                <div style={{ borderTop: '1px solid var(--border-color, #E5E7E1)', borderBottom: '1px solid var(--border-color, #E5E7E1)', padding: '14px 0', marginBottom: '18px', fontSize: '13px' }}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                                        <span style={{ color: 'var(--text-secondary, #70766F)' }}>Room Charges ({calculateNights()} × ${selectedRoom.pricePerNight}):</span>
                                        <span style={{ fontWeight: 600, color: 'var(--text-primary, #252925)' }}>${(calculateNights() * selectedRoom.pricePerNight).toFixed(2)}</span>
                                    </div>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px', color: 'var(--text-secondary, #70766F)' }}>
                                        <span>Estimated Taxes (12% GST):</span>
                                        <span style={{ fontWeight: 600, color: 'var(--accent-gold, #C5A46D)' }}>${(calculateNights() * selectedRoom.pricePerNight * 0.12).toFixed(2)}</span>
                                    </div>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px', color: 'var(--text-secondary, #70766F)' }}>
                                        <span>Service Charges:</span>
                                        <span style={{ fontWeight: 600, color: 'var(--text-primary, #252925)' }}>$15.00</span>
                                    </div>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700, fontSize: '16px', color: 'var(--primary-brand, #174A3A)', marginTop: '8px', paddingTop: '8px', borderTop: '1px dashed var(--border-color, #E5E7E1)' }}>
                                        <span>Total Estimated:</span>
                                        <span>${(calculateNights() * selectedRoom.pricePerNight * 1.12 + 15).toFixed(2)}</span>
                                    </div>
                                </div>

                                {bookingError && (
                                    <div style={{ backgroundColor: '#fee2e2', color: '#b91c1c', padding: '10px 14px', borderRadius: '8px', fontSize: '12px', marginBottom: '16px', border: '1px solid #fca5a5' }}>
                                        {bookingError}
                                    </div>
                                )}

                                <div style={{ display: 'flex', gap: '10px' }}>
                                    <button 
                                        type="button" 
                                        onClick={() => setSelectedRoom(null)}
                                        className="btn-secondary"
                                        style={{ flex: 1, padding: '11px' }}
                                    >
                                        Cancel
                                    </button>
                                    <button 
                                        type="button" 
                                        onClick={handleConfirmBooking}
                                        disabled={bookingLoading}
                                        className="btn-primary"
                                        style={{ flex: 1, padding: '11px' }}
                                    >
                                        {bookingLoading ? 'Reserving...' : 'Confirm Booking'}
                                    </button>
                                </div>
                            </>
                        ) : (
                            <div style={{ textAlign: 'center', padding: '12px 0 6px' }}>
                                <div style={{ 
                                    width: '56px', 
                                    height: '56px', 
                                    borderRadius: '50%', 
                                    backgroundColor: 'rgba(23, 74, 58, 0.1)', 
                                    color: 'var(--primary-brand, #174A3A)', 
                                    fontSize: '28px', 
                                    display: 'flex', 
                                    alignItems: 'center', 
                                    justifyContent: 'center',
                                    margin: '0 auto 12px' 
                                }}>
                                    ✓
                                </div>
                                <h3 style={{ fontFamily: 'Playfair Display, serif', fontSize: '20px', fontWeight: 700, color: 'var(--text-primary, #252925)', margin: 0 }}>
                                    Reservation Confirmed!
                                </h3>
                                <p style={{ fontSize: '13px', color: 'var(--text-secondary, #70766F)', marginTop: '6px' }}>
                                    Booking Reference ID:
                                </p>
                                <div style={{ 
                                    backgroundColor: 'var(--surface-muted, #F0F1EC)', 
                                    border: '1px dashed var(--accent-gold, #C5A46D)', 
                                    borderRadius: '8px', 
                                    padding: '8px 16px', 
                                    display: 'inline-block',
                                    margin: '6px 0 14px'
                                }}>
                                    <span style={{ fontSize: '18px', fontWeight: 700, color: 'var(--primary-brand, #174A3A)', letterSpacing: '0.5px', fontFamily: 'monospace' }}>
                                        #{String(bookingSuccess.id)}
                                    </span>
                                </div>
                                <p style={{ fontSize: '12px', color: 'var(--text-secondary, #70766F)', marginBottom: '22px', lineHeight: 1.5 }}>
                                    Room #{bookingSuccess.roomNumber} is reserved for {bookingSuccess.checkInDate} to {bookingSuccess.checkOutDate}. An in-app confirmation notification has been sent.
                                </p>
                                <div style={{ display: 'flex', gap: '10px' }}>
                                    <button 
                                        onClick={() => {
                                            setSelectedRoom(null);
                                            setBookingSuccess(null);
                                        }}
                                        className="btn-secondary"
                                        style={{ flex: 1, padding: '11px' }}
                                    >
                                        Close
                                    </button>
                                    <button 
                                        onClick={() => navigate('/my-bookings')}
                                        className="btn-primary"
                                        style={{ flex: 1, padding: '11px' }}
                                    >
                                        View My Bookings
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};

export default RoomSearch;
