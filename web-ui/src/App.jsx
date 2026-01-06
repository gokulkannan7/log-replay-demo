import { useState, useEffect, useMemo } from 'react'
import './App.css'

function App() {
  const [messages, setMessages] = useState([]);
  const [status, setStatus] = useState('DISCONNECTED');

  // No socket cleanup on unmount for this simple demo to keep connection alive if possible
  useEffect(() => {
    const ws = new WebSocket('ws://localhost:8888');
    setStatus('CONNECTING...');

    ws.onopen = () => {
      setStatus('LIVE STREAM');
      setMessages([]);
    };

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.status === 'COMPLETE') {
        setStatus('ANALYSIS COMPLETE');
      } else {
        setMessages(prev => [...prev, data]);
      }
    };

    ws.onclose = () => {
      setStatus('DISCONNECTED');
    };

    return () => ws.close();
  }, []);

  const stats = useMemo(() => {
    const total = messages.length;
    const mismatch = messages.filter(m => m.status === 'MISMATCH').length;
    const missing = messages.filter(m => m.status.includes('MISSING')).length;
    return { total, mismatch, missing };
  }, [messages]);

  // SMART ANALYSIS: Find the most frequent breaking tags
  const topOffenders = useMemo(() => {
    const counts = {};
    messages.forEach(msg => {
      if (msg.tagMismatches) {
        Object.keys(msg.tagMismatches).forEach(tag => {
          counts[tag] = (counts[tag] || 0) + 1;
        });
      }
    });
    return Object.entries(counts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 4); // Top 4
  }, [messages]);

  const errorMessages = useMemo(() => {
    return messages.filter(msg => msg.status !== 'MATCH');
  }, [messages]);

  return (
    <div className="container">
      <header className="dashboard-header">
        <h1>Log Replay <span>Audit</span></h1>
        <div className="status-indicator">{status}</div>
      </header>

      {/* NEW: Analytical Insights Panel */}
      <div className="insights-panel">
        {/* KPI Cards */}
        <div className="kpi-group">
          <div className="kpi-card error">
            <div className="kpi-val">{stats.mismatch}</div>
            <div className="kpi-lbl">Mismatches</div>
          </div>
          <div className="kpi-card warning">
            <div className="kpi-val">{stats.missing}</div>
            <div className="kpi-lbl">Missing</div>
          </div>
          <div className="kpi-card neutral">
            <div className="kpi-val">{stats.total}</div>
            <div className="kpi-lbl">Scanned</div>
          </div>
        </div>

        {/* Top Offenders List */}
        <div className="offenders-group">
          <h3>Top Breaking Tags</h3>
          {topOffenders.length === 0 ? (
            <div className="no-data">No mismatches yet</div>
          ) : (
            <div className="offenders-list">
              {topOffenders.map(([tag, count]) => (
                <div key={tag} className="offender-item">
                  <span className="of-tag">Tag {tag}</span>
                  <div className="of-bar-container">
                    <div className="of-bar" style={{ width: `${(count / stats.mismatch) * 100}%` }}></div>
                  </div>
                  <span className="of-count">{count}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Main Issues List */}
      <div className="results-list">
        {errorMessages.length === 0 && stats.total > 0 && (
          <div className="empty-state">No mismatches found. Perfect Match!</div>
        )}

        {errorMessages.map((item, index) => (
          <div key={index} className={`result-card-focused ${item.status}`}>

            <div className="card-context">
              <span className="context-label">ORDER ID</span>
              <span className="context-id">{item.orderId}</span>
              <span className={`mini-badge ${item.status}`}>{item.status}</span>
            </div>

            <div className="card-problems">
              {item.status.includes("MISSING") ? (
                <div className="missing-text">
                  Message missing in {item.status.includes("ORIGINAL") ? "Original" : "Replay"}
                </div>
              ) : (
                <div className="tag-row">
                  {Object.entries(item.tagMismatches).map(([tag, values]) => (
                    <div key={tag} className="tag-item">
                      <span className="t-name">Tag {tag}</span>
                      <span className="t-val">{values[1]}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export default App
