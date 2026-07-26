import React, { useState, useEffect } from 'react';

export default function App() {
  const [healthStatus, setHealthStatus] = useState('Connecting...');
  const [subscriptions, setSubscriptions] = useState([]);
  const [searchPrompt, setSearchPrompt] = useState('');

  useEffect(() => {
    // Check Backend Health Status
    fetch('/api/v1/health')
      .then((res) => res.json())
      .then((data) => setHealthStatus(`Backend Online (${data.service} v${data.version})`))
      .catch(() => setHealthStatus('Backend Offline / Reconnecting...'));

    // Fetch Subscriptions catalog
    fetch('/api/v1/subscriptions')
      .then((res) => res.json())
      .then((data) => setSubscriptions(data))
      .catch((err) => console.error('Failed to load subscriptions:', err));
  }, []);

  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto' }}>
      {/* Navigation & Header */}
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem', borderBottom: '1px solid #27272a', paddingBottom: '1rem' }}>
        <div>
          <h1 style={{ margin: 0, fontSize: '2rem', color: '#ff0000', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span>▶</span> UTubeHub
          </h1>
          <p style={{ margin: '0.25rem 0 0 0', color: '#a1a1aa' }}>YouTube Subscription Management & AI Search Hub</p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: '0.85rem', color: healthStatus.includes('Online') ? '#4ade80' : '#f87171', marginBottom: '0.5rem' }}>
            ● {healthStatus}
          </div>
          <a 
            href="/swagger-ui.html" 
            target="_blank" 
            rel="noreferrer"
            style={{ color: '#38bdf8', fontSize: '0.9rem', textDecoration: 'none', background: '#1e293b', padding: '0.4rem 0.8rem', borderRadius: '6px', border: '1px solid #334155' }}
          >
            📄 Swagger API Docs
          </a>
        </div>
      </header>

      {/* AI Prompt Search Input */}
      <section style={{ marginBottom: '2.5rem', background: '#18181b', padding: '1.5rem', borderRadius: '12px', border: '1px solid #27272a' }}>
        <h3 style={{ marginTop: 0, color: '#f4f4f5' }}>✨ AI Prompt-Based Search</h3>
        <p style={{ color: '#a1a1aa', fontSize: '0.9rem' }}>Ask anything across all your subscribed channels (e.g. "Show me Python tutorials under 15 minutes uploaded this week")</p>
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <input
            type="text"
            placeholder="Type your prompt query here..."
            value={searchPrompt}
            onChange={(e) => setSearchPrompt(e.target.value)}
            style={{
              flex: 1,
              padding: '0.75rem 1rem',
              borderRadius: '8px',
              border: '1px solid #3f3f46',
              background: '#09090b',
              color: '#fff',
              fontSize: '1rem'
            }}
          />
          <button
            onClick={() => alert(`AI Search for: "${searchPrompt}" will be processed in Phase 4!`)}
            style={{
              padding: '0.75rem 1.5rem',
              borderRadius: '8px',
              border: 'none',
              background: '#cc0000',
              color: '#fff',
              fontWeight: 'bold',
              cursor: 'pointer'
            }}
          >
            Search
          </button>
        </div>
      </section>

      {/* Subscriptions Grid */}
      <section>
        <h2 style={{ color: '#f4f4f5', marginBottom: '1rem' }}>Subscribed Channels</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '1.5rem' }}>
          {subscriptions.map((sub, idx) => (
            <div key={idx} style={{ background: '#18181b', padding: '1.25rem', borderRadius: '10px', border: '1px solid #27272a' }}>
              <h3 style={{ margin: '0 0 0.5rem 0', color: '#ffffff' }}>{sub.title}</h3>
              <p style={{ margin: '0 0 0.5rem 0', color: '#a1a1aa', fontSize: '0.9rem' }}>
                Subscribers: <strong>{(sub.subscriberCount / 1000000).toFixed(1)}M</strong>
              </p>
              <p style={{ margin: 0, color: '#a1a1aa', fontSize: '0.9rem' }}>
                Videos: <strong>{sub.videoCount}</strong>
              </p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
