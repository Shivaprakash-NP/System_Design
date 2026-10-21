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
                    checkOut: checkOutDate
                }
            });
            setRooms(res.data);
        } catch (err) {
            // Fallback to general available rooms
            try {
                const res2 = await api.get(`/rooms/available/${selectedHotel.id}`);
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
    }, [selectedHotel, checkInDate, checkOutDate]);

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

    return (
        <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '16px' }}>
            {/* Header & Search Bar */}
            <div style={{ backgroundColor: '#ffffff', padding: '24px', borderRadius: '12px', border: '1px solid #e2e8f0', marginBottom: '24px', boxShadow: '0 2px 10px rgba(0,0,0,0.03)' }}>
                <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', marginBottom: '4px' }}>Discover & Book Luxury Accommodations</h1>
                <p style={{ fontSize: '14px', color: '#64748b', marginBottom: '20px' }}>Powered by Redis Cache, Autocomplete & Distributed Invariant Protection</p>

                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '16px', alignItems: 'center' }}>
                    <SearchBarWithAutocomplete onSelect={(term) => setSearchQuery(term)} />

                    <select 
                        value={cityFilter} 
                        onChange={(e) => setCityFilter(e.target.value)}
                        style={{ padding: '10px 14px', fontSize: '14px', minWidth: '160px' }}
                    >
                        <option value="">All Cities</option>
                        <option value="Chennai">Chennai</option>
                        <option value="Mumbai">Mumbai</option>
                        <option value="Bengaluru">Bengaluru</option>
                    </select>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <label style={{ fontSize: '13px', fontWeight: 600, color: '#475569' }}>Check-In:</label>
                        <input 
                            type="date" 
                            value={checkInDate} 
                            min={today}
                            onChange={(e) => setCheckInDate(e.target.value)} 
                        />
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <label style={{ fontSize: '13px', fontWeight: 600, color: '#475569' }}>Check-Out:</label>
                        <input 
                            type="date" 
                            value={checkOutDate} 
                            min={checkInDate}
                            onChange={(e) => setCheckOutDate(e.target.value)} 
                        />
                    </div>
                </div>
            </div>

            {/* Hotel Cards Carousel / Grid */}
            <div style={{ marginBottom: '24px' }}>
                <h2 style={{ fontSize: '18px', fontWeight: 600, color: '#0f172a', marginBottom: '12px' }}>Featured Hotels ({hotels.length})</h2>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '16px' }}>
                    {hotels.map(h => (
                        <div 
                            key={h.id}
                            onClick={() => setSelectedHotel(h)}
                            style={{
                                backgroundColor: '#ffffff',
                                borderRadius: '10px',
                                border: selectedHotel?.id === h.id ? '2px solid #0d9488' : '1px solid #e2e8f0',
                                overflow: 'hidden',
                                cursor: 'pointer',
                                transition: 'all 0.2s ease',
                                boxShadow: selectedHotel?.id === h.id ? '0 4px 12px rgba(13,148,136,0.15)' : 'none'
                            }}
                        >
                            <img 
                                src={h.imageUrl || 'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=500'} 
                                alt={h.name} 
                                style={{ width: '100%', height: '140px', objectFit: 'cover' }} 
                            />
                            <div style={{ padding: '12px' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <h3 style={{ fontSize: '15px', fontWeight: 700, color: '#0f172a' }}>{h.name}</h3>
                                    <span style={{ fontSize: '12px', color: '#d97706', fontWeight: 700 }}>★ {h.rating}</span>
                                </div>
                                <p style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>📍 {h.city}</p>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginTop: '8px' }}>
                                    {h.amenities?.slice(0, 3).map((a, i) => (
                                        <span key={i} style={{ fontSize: '10px', backgroundColor: '#f1f5f9', padding: '2px 6px', borderRadius: '4px', color: '#475569' }}>
                                            {a}
                                        </span>
                                    ))}
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            {/* Room Availability Section */}
            {selectedHotel && (
                <div style={{ backgroundColor: '#ffffff', padding: '24px', borderRadius: '12px', border: '1px solid #e2e8f0' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                        <div>
                            <h2 style={{ fontSize: '18px', fontWeight: 700, color: '#0f172a' }}>
                                Available Rooms at {selectedHotel.name}
                            </h2>
                            <p style={{ fontSize: '13px', color: '#64748b' }}>
                                Dates: {checkInDate} to {checkOutDate} ({calculateNights()} night{calculateNights() > 1 ? 's' : ''})
                            </p>
                        </div>
                        <span style={{ fontSize: '12px', backgroundColor: '#f0fdfa', color: '#0d9488', padding: '4px 10px', borderRadius: '6px', fontWeight: 600 }}>
                            ⚡ Cached via Redis Key-Value Store
                        </span>
                    </div>

                    {rooms.length === 0 ? (
                        <div style={{ textAlign: 'center', padding: '40px', color: '#94a3b8' }}>
                            <p style={{ fontSize: '16px' }}>No rooms available for the selected dates.</p>
                            <p style={{ fontSize: '13px', marginTop: '4px' }}>Try selecting different dates or another hotel.</p>
                        </div>
                    ) : (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '16px' }}>
                            {rooms.map(room => (
                                <div 
                                    key={room.id}
                                    style={{
                                        border: '1px solid #e2e8f0',
                                        borderRadius: '8px',
                                        padding: '16px',
                                        backgroundColor: '#ffffff',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        justifyContent: 'space-between'
                                    }}
                                >
                                    <div>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                                            <span style={{ fontSize: '16px', fontWeight: 700, color: '#0f172a' }}>
                                                Room {room.roomNumber}
                                            </span>
                                            <span className={`badge badge-${room.housekeepingStatus?.toLowerCase() || 'clean'}`}>
                                                {room.housekeepingStatus}
                                            </span>
                                        </div>

                                        <p style={{ fontSize: '13px', fontWeight: 600, color: '#0d9488' }}>{room.type} SUITE</p>
                                        <p style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>Max Occupancy: {room.capacity || 2} Guests</p>

                                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', margin: '10px 0' }}>
                                            {room.amenities?.map((am, idx) => (
                                                <span key={idx} style={{ fontSize: '11px', backgroundColor: '#f8fafc', border: '1px solid #e2e8f0', padding: '2px 6px', borderRadius: '4px', color: '#475569' }}>
                                                    ✓ {am}
                                                </span>
                                            ))}
                                        </div>
                                    </div>

                                    <div style={{ marginTop: '16px', paddingTop: '12px', borderTop: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                        <div>
                                            <span style={{ fontSize: '18px', fontWeight: 700, color: '#0f172a' }}>${room.pricePerNight}</span>
                                            <span style={{ fontSize: '12px', color: '#94a3b8' }}> / night</span>
                                        </div>
                                        <button 
                                            onClick={() => {
                                                setSelectedRoom(room);
                                                setBookingSuccess(null);
                                                setBookingError('');
                                            }}
                                            className="btn-primary"
                                            style={{ padding: '8px 14px', fontSize: '13px' }}
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
                    backgroundColor: 'rgba(15, 23, 42, 0.6)',
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center',
                    zIndex: 1000
                }}>
                    <div style={{
                        backgroundColor: '#ffffff',
                        borderRadius: '12px',
                        padding: '28px',
                        width: '90%',
                        maxWidth: '460px',
                        boxShadow: '0 20px 25px -5px rgba(0,0,0,0.2)'
                    }}>
                        {!bookingSuccess ? (
                            <>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                                    <h3 style={{ fontSize: '18px', fontWeight: 700, color: '#0f172a' }}>Confirm Stay Reservation</h3>
                                    <button onClick={() => setSelectedRoom(null)} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer' }}>✕</button>
                                </div>

                                <div style={{ backgroundColor: '#f8fafc', padding: '14px', borderRadius: '8px', marginBottom: '16px', fontSize: '13px' }}>
                                    <p><strong>Hotel:</strong> {selectedHotel?.name}</p>
                                    <p><strong>Room:</strong> #{selectedRoom.roomNumber} ({selectedRoom.type})</p>
                                    <p><strong>Dates:</strong> {checkInDate} to {checkOutDate} ({calculateNights()} night{calculateNights() > 1 ? 's' : ''})</p>
                                    <p><strong>Guest:</strong> {user?.fullName || 'John Guest'} ({user?.email || 'guest@hotel.demo'})</p>
                                </div>

                                {/* Price Breakdown */}
                                <div style={{ borderTop: '1px solid #e2e8f0', borderBottom: '1px solid #e2e8f0', padding: '12px 0', marginBottom: '16px', fontSize: '13px' }}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                                        <span>Room Charges ({calculateNights()} × ${selectedRoom.pricePerNight}):</span>
                                        <span>${(calculateNights() * selectedRoom.pricePerNight).toFixed(2)}</span>
                                    </div>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px', color: '#64748b' }}>
                                        <span>Estimated Taxes (12% GST):</span>
                                        <span>${(calculateNights() * selectedRoom.pricePerNight * 0.12).toFixed(2)}</span>
                                    </div>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px', color: '#64748b' }}>
                                        <span>Service Charges:</span>
                                        <span>$15.00</span>
                                    </div>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700, fontSize: '15px', color: '#0f172a', marginTop: '6px' }}>
                                        <span>Total Estimated:</span>
                                        <span>${(calculateNights() * selectedRoom.pricePerNight * 1.12 + 15).toFixed(2)}</span>
                                    </div>
                                </div>

                                {bookingError && (
                                    <div style={{ backgroundColor: '#fee2e2', color: '#b91c1c', padding: '10px', borderRadius: '6px', fontSize: '12px', marginBottom: '14px' }}>
                                        {bookingError}
                                    </div>
                                )}

                                <div style={{ display: 'flex', gap: '10px' }}>
                                    <button 
                                        type="button" 
                                        onClick={() => setSelectedRoom(null)}
                                        className="btn-secondary"
                                        style={{ flex: 1, padding: '10px' }}
                                    >
                                        Cancel
                                    </button>
                                    <button 
                                        type="button" 
                                        onClick={handleConfirmBooking}
                                        disabled={bookingLoading}
                                        className="btn-primary"
                                        style={{ flex: 1, padding: '10px' }}
                                    >
                                        {bookingLoading ? 'Reserving...' : 'Confirm Booking'}
                                    </button>
                                </div>
                            </>
                        ) : (
                            <div style={{ textAlign: 'center', padding: '10px 0' }}>
                                <div style={{ fontSize: '42px', marginBottom: '8px' }}>🎉</div>
                                <h3 style={{ fontSize: '18px', fontWeight: 700, color: '#0f172a' }}>Reservation Confirmed!</h3>
                                <p style={{ fontSize: '13px', color: '#64748b', marginTop: '6px' }}>
                                    Booking Reference ID:
                                </p>
                                <p style={{ fontSize: '16px', fontWeight: 700, color: '#0d9488', margin: '8px 0', letterSpacing: '0.5px' }}>
                                    #{bookingSuccess.id}
                                </p>
                                <p style={{ fontSize: '12px', color: '#64748b', marginBottom: '20px' }}>
                                    Room #{bookingSuccess.roomNumber} is reserved for {bookingSuccess.checkInDate} to {bookingSuccess.checkOutDate}. An in-app confirmation notification has been sent.
                                </p>
                                <div style={{ display: 'flex', gap: '10px' }}>
                                    <button 
                                        onClick={() => {
                                            setSelectedRoom(null);
                                            setBookingSuccess(null);
                                        }}
                                        className="btn-secondary"
                                        style={{ flex: 1, padding: '10px' }}
                                    >
                                        Close
                                    </button>
                                    <button 
                                        onClick={() => navigate('/my-bookings')}
                                        className="btn-primary"
                                        style={{ flex: 1, padding: '10px' }}
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
