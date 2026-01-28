import { useState, useEffect, useRef, useCallback } from 'react';

const useWebSocket = (url) => {
    const [isConnected, setIsConnected] = useState(false);
    const [messages, setMessages] = useState([]);
    const [logs, setLogs] = useState([]);
    const wsRef = useRef(null);
    const reconnectTimeoutRef = useRef(null);
    const reconnectAttemptsRef = useRef(0);

    const addLog = useCallback((message, type = 'info') => {
        const timestamp = new Date().toLocaleTimeString();
        setLogs(prev => [...prev, { timestamp, message, type }]);
        console.log(`[${type.toUpperCase()}] ${message}`);
    }, []);

    const connect = useCallback(() => {
        try {
            addLog(`Attempting to connect to ${url}...`, 'info');

            const ws = new WebSocket(url);

            ws.onopen = () => {
                setIsConnected(true);
                reconnectAttemptsRef.current = 0;
                addLog('WebSocket connection established successfully', 'success');
            };

            ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);

                    // --- STACK TRACE PROTECTION & STRICT VALIDATION ---
                    // Explicitly reject if:
                    // 1. Data is null/empty
                    // 2. data.orderId is missing, "undefined", or null
                    // 3. data.tagMismatches is missing or empty
                    const orderId = data?.orderId;
                    const tagMismatches = data?.tagMismatches || {};
                    const mismatchCount = Object.keys(tagMismatches).length;

                    if (!orderId || orderId === 'undefined' || mismatchCount === 0) {
                        // Log locally to console for debugging, but don't show in UI to keep it clean
                        console.warn('REJECTED: Received invalid or empty order payload:', data);
                        return;
                    }

                    addLog(`[VALIDATED] Received Order: ${orderId} | Mismatches: ${mismatchCount}`, 'info');

                    setMessages(prev => {
                        const existingIndex = prev.findIndex(msg => msg.orderId === orderId);
                        if (existingIndex !== -1) {
                            const updated = [...prev];
                            updated[existingIndex] = data;
                            return updated;
                        } else {
                            return [...prev, data];
                        }
                    });
                } catch (error) {
                    addLog(`WS JSON Error: ${error.message}`, 'error');
                }
            };

            ws.onerror = (error) => {
                addLog('WebSocket error occurred', 'error');
                console.error('WebSocket error:', error);
            };

            ws.onclose = () => {
                setIsConnected(false);
                setMessages([]); // Clear all orders on disconnect
                addLog('WebSocket connection closed - UI cleared', 'error');

                // Attempt to reconnect with exponential backoff
                const delay = Math.min(1000 * Math.pow(2, reconnectAttemptsRef.current), 30000);
                reconnectAttemptsRef.current += 1;

                addLog(`Reconnecting in ${delay / 1000} seconds... (Attempt ${reconnectAttemptsRef.current})`, 'info');

                reconnectTimeoutRef.current = setTimeout(() => {
                    connect();
                }, delay);
            };

            wsRef.current = ws;
        } catch (error) {
            addLog(`Connection error: ${error.message}`, 'error');
        }
    }, [url, addLog]);

    useEffect(() => {
        connect();

        return () => {
            if (reconnectTimeoutRef.current) {
                clearTimeout(reconnectTimeoutRef.current);
            }
            if (wsRef.current) {
                wsRef.current.close();
            }
        };
    }, [connect]);

    const clearLogs = useCallback(() => {
        setLogs([]);
    }, []);

    return {
        isConnected,
        messages,
        logs,
        clearLogs
    };
};

export default useWebSocket;
