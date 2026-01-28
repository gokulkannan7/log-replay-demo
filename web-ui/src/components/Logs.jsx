import React, { useEffect, useRef } from 'react';

const Logs = ({ logs, onClear }) => {
    const logsEndRef = useRef(null);

    const scrollToBottom = () => {
        logsEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    useEffect(() => {
        scrollToBottom();
    }, [logs]);

    return (
        <div className="logs-section">
            <div className="logs-header">
                <h3 className="logs-title">📋 Connection Logs</h3>
                <button className="clear-logs-button" onClick={onClear}>
                    Clear Logs
                </button>
            </div>
            <div className="logs-container">
                {logs.length === 0 ? (
                    <div className="log-entry">No logs yet...</div>
                ) : (
                    logs.map((log, index) => (
                        <div key={index} className={`log-entry ${log.type}`}>
                            <span className="log-timestamp">[{log.timestamp}]</span>
                            {log.message}
                        </div>
                    ))
                )}
                <div ref={logsEndRef} />
            </div>
        </div>
    );
};

export default Logs;
