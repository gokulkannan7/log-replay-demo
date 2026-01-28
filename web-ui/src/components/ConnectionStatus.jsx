import React from 'react';

const ConnectionStatus = ({ isConnected }) => {
    return (
        <div className={`connection-status ${isConnected ? 'online' : 'offline'}`}>
            <div className={`status-indicator ${isConnected ? 'online' : 'offline'}`}></div>
            <span className="status-text">
                {isConnected ? 'Connected' : 'Disconnected'}
            </span>
        </div>
    );
};

export default ConnectionStatus;
