import React, { useState } from 'react';
import api from '../services/api';

const BookingFlow = () => {
    const [bookingData, setBookingData] = useState({
        hotelId: 'hotel_123',
        roomId: 'room_456',
        checkInDate: '2027-01-10',
        checkOutDate: '2027-01-15'
    });
    const [message, setMessage] = useState('');

    const handleBooking = async (e) => {
        e.preventDefault();
        setMessage('Processing booking...');

        try {
            // guestId is sent for the rate limiter tracking, though usually derived from JWT
            const response = await api.post('/bookings', {
                ...bookingData,
                guestId: 'guest_999' 
            });
            setMessage(`Booking Confirmed! ID: ${response.data.id}`);
        } catch (error) {
            if (error.response && error.response.status === 429) {
                setMessage("Rate limit exceeded. Please wait a moment and try again.");
            } else {
                setMessage("An error occurred during booking.");
            }
        }
    };

    return (
        <div style={{ padding: '20px', maxWidth: '400px', margin: '0 auto' }}>
            <h2>Confirm Booking</h2>
            <form onSubmit={handleBooking} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                <button type="submit" style={{ padding: '10px', cursor: 'pointer' }}>
                    Confirm Reservation
                </button>
            </form>
            {message && <p style={{ marginTop: '15px', fontWeight: 'bold' }}>{message}</p>}
        </div>
    );
};

export default BookingFlow;