import React from 'react';

const OrderDetailsModal = ({ order, onClose }) => {
    if (!order) return null;

    // Helper function to check if a tag is a profile tag
    const isProfileTag = (tagName) => {
        return tagName.startsWith('8019.');
    };

    // Helper function to normalize tag name (remove leading "-")
    const normalizeTagName = (tagName) => {
        return tagName.startsWith('-') ? tagName.substring(1) : tagName;
    };

    const mismatchEntries = Object.entries(order.tagMismatches || {});

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2 className="modal-title">Order Details</h2>
                    <button className="close-button" onClick={onClose}>
                        ✕
                    </button>
                </div>

                <div className="modal-content">
                    <div className="order-details-header">
                        <div className="detail-group">
                            <span className="detail-label">Order ID</span>
                            <span className="detail-value">{order.orderId}</span>
                        </div>
                        <div className="detail-group">
                            <span className="detail-label">Status</span>
                            <span className={`status-badge ${order.status.toLowerCase()}`}>
                                {order.status}
                            </span>
                        </div>
                        <div className="detail-group">
                            <span className="detail-label">Process Type</span>
                            <span className="detail-value">{order.processType.toUpperCase()}</span>
                        </div>
                        <div className="detail-group">
                            <span className="detail-label">Total Mismatches</span>
                            <span className="detail-value">{mismatchEntries.length}</span>
                        </div>
                    </div>

                    <div className="mismatches-section">
                        <h3 className="section-title">
                            🔍 Tag Mismatches
                        </h3>

                        {mismatchEntries.length === 0 ? (
                            <div className="empty-state">
                                <div className="empty-state-icon">✓</div>
                                <div className="empty-state-title">No Mismatches</div>
                                <div className="empty-state-description">
                                    All tags match perfectly
                                </div>
                            </div>
                        ) : (
                            <div className="mismatches-list">
                                {mismatchEntries.map(([tagName, values]) => {
                                    const normalizedTagName = normalizeTagName(tagName);
                                    const isProfile = isProfileTag(normalizedTagName);

                                    return (
                                        <div
                                            key={tagName}
                                            className={`mismatch-card ${isProfile ? 'profile' : ''}`}
                                        >
                                            <div className="mismatch-header">
                                                <span className={`tag-name ${isProfile ? 'profile' : ''}`}>
                                                    Tag {normalizedTagName}
                                                </span>
                                                {isProfile && (
                                                    <span className="profile-badge">Profile</span>
                                                )}
                                            </div>
                                            <div className="mismatch-values">
                                                {Array.isArray(values) ? (
                                                    values.map((value, idx) => (
                                                        <span key={idx} className="value-chip">
                                                            {value}
                                                        </span>
                                                    ))
                                                ) : (
                                                    <span className="value-chip">{String(values)}</span>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default OrderDetailsModal;
