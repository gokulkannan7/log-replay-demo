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
                    addLog(`Received message for order: ${data.orderId}`, 'info');
                    setMessages(prev => [...prev, data]);
                } catch (error) {
                    addLog(`Error parsing message: ${error.message}`, 'error');
                }
            };

            ws.onerror = (error) => {
                addLog('WebSocket error occurred', 'error');
                console.error('WebSocket error:', error);
            };

            ws.onclose = () => {
                setIsConnected(false);
                addLog('WebSocket connection closed', 'error');

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
