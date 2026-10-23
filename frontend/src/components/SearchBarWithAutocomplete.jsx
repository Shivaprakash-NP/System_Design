import React, { useState, useEffect } from 'react';
import api from '../services/api';

const SearchBarWithAutocomplete = ({ onSelect }) => {
    const [query, setQuery] = useState('');
    const [suggestions, setSuggestions] = useState([]);
    const [isTyping, setIsTyping] = useState(false);

    useEffect(() => {
        const fetchSuggestions = async () => {
            if (query.trim().length === 0) {
                setSuggestions([]);
                return;
            }

            try {
                const response = await api.get(`/search/autocomplete?q=${encodeURIComponent(query)}`);
                setSuggestions(response.data);
            } catch (error) {
                console.error("Autocomplete error", error);
            }
        };

        const debounceTimer = setTimeout(() => {
            fetchSuggestions();
        }, 200);

        return () => clearTimeout(debounceTimer);
    }, [query]);

    const handleSelect = (term) => {
        setQuery(term);
        setSuggestions([]);
        setIsTyping(false);
        if (onSelect) {
            onSelect(term);
        }
    };

    return (
        <div style={{ position: 'relative', width: '100%', maxWidth: '480px' }}>
            <div style={{ display: 'flex', gap: '8px' }}>
                <input
                    type="text"
                    value={query}
                    onChange={(e) => {
                        setQuery(e.target.value);
                        setIsTyping(true);
                    }}
                    onFocus={() => setIsTyping(true)}
                    onBlur={() => setTimeout(() => setIsTyping(false), 250)}
                    placeholder="Search hotel name, destination, or city..."
                    style={{
                        width: '100%',
                        padding: '11px 16px',
                        fontSize: '13.5px',
                        borderRadius: '8px',
                        border: '1px solid #E5E7E1',
                        backgroundColor: '#FFFFFF',
                        color: '#252925'
                    }}
                />
                {query && (
                    <button 
                        type="button" 
                        onClick={() => handleSelect('')}
                        style={{
                            background: '#F0F1EC',
                            border: '1px solid #E5E7E1',
                            padding: '0 12px',
                            borderRadius: '8px',
                            color: '#70766F',
                            cursor: 'pointer',
                            fontSize: '12px'
                        }}
                    >
                        ✕
                    </button>
                )}
            </div>

            {isTyping && suggestions.length > 0 && (
                <ul style={{
                    position: 'absolute',
                    top: '100%',
                    left: 0,
                    right: 0,
                    backgroundColor: '#FFFFFF',
                    border: '1px solid #E5E7E1',
                    borderRadius: '10px',
                    boxShadow: '0 12px 30px rgba(23, 74, 58, 0.12)',
                    listStyle: 'none',
                    margin: '6px 0 0 0',
                    padding: '6px 0',
                    zIndex: 1000,
                    maxHeight: '240px',
                    overflowY: 'auto'
                }}>
                    {suggestions.map((suggestion, index) => (
                        <li 
                            key={index}
                            onMouseDown={() => handleSelect(suggestion)}
                            style={{ 
                                padding: '10px 16px', 
                                cursor: 'pointer', 
                                borderBottom: index < suggestions.length - 1 ? '1px solid #F0F1EC' : 'none',
                                fontSize: '13px',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '10px',
                                color: '#252925',
                                transition: 'background-color 0.15s ease'
                            }}
                            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#FAF9F6'}
                            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                        >
                            <span style={{ color: '#C5A46D', fontSize: '12px' }}>📍</span>
                            <span style={{ fontWeight: 500 }}>{suggestion}</span>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
};

export default SearchBarWithAutocomplete;