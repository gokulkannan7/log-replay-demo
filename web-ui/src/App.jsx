import React, { useState, useMemo } from 'react';
import useWebSocket from './hooks/useWebSocket';
import ConnectionStatus from './components/ConnectionStatus';
import OrderCard from './components/OrderCard';
import OrderDetailsModal from './components/OrderDetailsModal';
import Logs from './components/Logs';
import './index.css';

// WebSocket URL - matches backend port 8888
const WS_URL = 'ws://localhost:8888';

function App() {
    const { isConnected, messages, logs, clearLogs } = useWebSocket(WS_URL);
    const [activeTab, setActiveTab] = useState('onc');
    const [selectedOrder, setSelectedOrder] = useState(null);

    // Separate orders by process type
    const { oncOrders, omsOrders } = useMemo(() => {
        const onc = [];
        const oms = [];

        messages.forEach(message => {
            if (message.processType === 'onc') {
                onc.push(message);
            } else if (message.processType === 'oms') {
                oms.push(message);
            }
        });

        return { oncOrders: onc, omsOrders: oms };
    }, [messages]);

    const currentOrders = activeTab === 'onc' ? oncOrders : omsOrders;

    return (
        <div className="app">
            <header className="header">
                <div className="header-content">
                    <div className="header-title">
                        <span className="icon">📊</span>
                        <h1>Log Replay Monitor</h1>
                    </div>
                    <ConnectionStatus isConnected={isConnected} />
                </div>
            </header>

            <main className="container">
                <div className="tabs">
                    <button
                        className={`tab ${activeTab === 'onc' ? 'active' : ''}`}
                        onClick={() => setActiveTab('onc')}
                    >
                        ONC Engine
                        {oncOrders.length > 0 && (
                            <span className="tab-badge">{oncOrders.length}</span>
                        )}
                    </button>
                    <button
                        className={`tab ${activeTab === 'oms' ? 'active' : ''}`}
                        onClick={() => setActiveTab('oms')}
                    >
                        OMS Engine
                        {omsOrders.length > 0 && (
                            <span className="tab-badge">{omsOrders.length}</span>
                        )}
                    </button>
                </div>

                {currentOrders.length === 0 ? (
                    <div className="empty-state">
                        <div className="empty-state-icon">📭</div>
                        <div className="empty-state-title">No Orders Yet</div>
                        <div className="empty-state-description">
                            {isConnected
                                ? `Waiting for ${activeTab.toUpperCase()} orders...`
                                : 'Connect to the server to see orders'}
                        </div>
                    </div>
                ) : (
                    <div className="orders-grid">
                        {currentOrders.map((order, index) => (
                            <OrderCard
                                key={`${order.orderId}-${index}`}
                                order={order}
                                onClick={setSelectedOrder}
                            />
                        ))}
                    </div>
                )}

                <Logs logs={logs} onClear={clearLogs} />
            </main>

            {selectedOrder && (
                <OrderDetailsModal
                    order={selectedOrder}
                    onClose={() => setSelectedOrder(null)}
                />
            )}
        </div>
    );
}

export default App;
