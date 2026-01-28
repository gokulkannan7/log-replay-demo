import React from 'react';

const OrderCard = ({ order, onClick }) => {
    const mismatchCount = Object.keys(order.tagMismatches || {}).length;

    return (
        <div className="order-card" onClick={() => onClick(order)}>
            <div className="order-header">
                <div className="order-id">{order.orderId}</div>
                <span className={`status-badge ${order.status.toLowerCase()}`}>
                    {order.status}
                </span>
            </div>
            <div className="order-stats">
                <div className="stat">
                    <div className="stat-label">Mismatches</div>
                    <div className="stat-value">{mismatchCount}</div>
                </div>
            </div>
        </div>
    );
};

export default OrderCard;
