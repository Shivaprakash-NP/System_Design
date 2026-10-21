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
                    placeholder="Search hotel name, city (e.g. Chennai, Taj, Leela)..."
                    style={{ width: '100%', padding: '10px 14px', fontSize: '14px' }}
                />
                {query && (
                    <button 
                        type="button" 
                        onClick={() => handleSelect('')}
                        style={{ background: '#f1f5f9', border: '1px solid #e2e8f0', padding: '0 12px', borderRadius: '6px', color: '#64748b' }}
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
                    backgroundColor: '#ffffff',
                    border: '1px solid #e2e8f0',
                    borderRadius: '8px',
                    boxShadow: '0 8px 20px rgba(0,0,0,0.1)',
                    listStyle: 'none',
                    margin: '4px 0 0 0',
                    padding: '4px 0',
                    zIndex: 1000,
                    maxHeight: '240px',
                    overflowY: 'auto'
                }}>
                    {suggestions.map((suggestion, index) => (
                        <li 
                            key={index}
                            onMouseDown={() => handleSelect(suggestion)}
                            style={{ 
                                padding: '10px 14px', 
                                cursor: 'pointer', 
                                borderBottom: index < suggestions.length - 1 ? '1px solid #f8fafc' : 'none',
                                fontSize: '13px',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px',
                                color: '#1e293b'
                            }}
                            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f0fdfa'}
                            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                        >
                            <span style={{ color: '#0d9488' }}>🔍</span>
                            <span>{suggestion}</span>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
};

export default SearchBarWithAutocomplete;
