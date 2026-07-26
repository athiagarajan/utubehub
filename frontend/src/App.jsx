import React, { useState, useEffect } from 'react';

export default function App() {
  const [healthStatus, setHealthStatus] = useState('Connecting...');
  const [activeUserEmail, setActiveUserEmail] = useState('Default Account');
  const [googleUser, setGoogleUser] = useState(null);
  const [userToken, setUserToken] = useState(null);

  const [subscriptions, setSubscriptions] = useState([]);
  const [ownChannel, setOwnChannel] = useState(null);
  const [mainNavTab, setMainNavTab] = useState('subscribed'); // 'subscribed' | 'uploaded'
  
  // Subscribed channels state
  const [selectedSubscribedChannel, setSelectedSubscribedChannel] = useState(null);
  const [subscribedTab, setSubscribedTab] = useState('videos'); // 'videos' | 'shorts' | 'playlists'
  const [subscribedContent, setSubscribedContent] = useState([]);
  const [isSubscribedSyncing, setIsSubscribedSyncing] = useState(false);

  // Your Contents state
  const [yourContentTab, setYourContentTab] = useState('videos'); // 'videos' | 'playlists' | 'live' | 'posts'
  const [yourContentData, setYourContentData] = useState([]);
  const [isYourContentLoading, setIsYourContentLoading] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);

  const [activeVideoId, setActiveVideoId] = useState(null);
  const [searchPrompt, setSearchPrompt] = useState('');
  const [customEmailInput, setCustomEmailInput] = useState('');

  useEffect(() => {
    // Check Backend Health Status
    fetch('/api/v1/health')
      .then((res) => res.json())
      .then((data) => setHealthStatus(`Backend Online (${data.service} v${data.version})`))
      .catch(() => setHealthStatus('Backend Offline / Reconnecting...'));

    // Check if real Google user is authenticated via OAuth
    fetch('/api/v1/auth/user')
      .then((res) => res.json())
      .then((data) => {
        if (data.authenticated && data.email) {
          setGoogleUser(data);
          setActiveUserEmail(data.email);
          loadAllChannels(data.email);
        } else {
          setActiveUserEmail('account1@gmail.com');
          loadAllChannels('account1@gmail.com');
        }
      })
      .catch(() => {
        setActiveUserEmail('account1@gmail.com');
        loadAllChannels('account1@gmail.com');
      });
  }, []);

  const switchUserAccount = (email) => {
    setActiveUserEmail(email);
    loadAllChannels(email).then(() => {
      triggerFullSync(email);
    });
  };

  const loadAllChannels = (email = activeUserEmail) => {
    const userId = email.includes('2') ? 'user2' : 'user1';

    return fetch(`/api/v1/subscriptions?userId=${userId}`)
      .then((res) => res.json())
      .then((data) => {
        const mine = data.find((ch) => ch.isMine);
        const subbed = data.filter((ch) => !ch.isMine);

        setOwnChannel(mine || null);
        setSubscriptions(subbed);

        if (subbed.length > 0) {
          selectSubscribedChannel(subbed[0], 'videos');
        } else {
          setSelectedSubscribedChannel(null);
          setSubscribedContent([]);
        }

        loadYourContent('videos', email);
        return { mine, subbed };
      })
      .catch((err) => console.error('Failed to load channels:', err));
  };

  const triggerFullSync = (email = activeUserEmail) => {
    setIsSyncing(true);
    setIsSubscribedSyncing(true);
    const userId = email.includes('2') ? 'user2' : 'user1';
    const headers = userToken ? { 'Authorization': `Bearer ${userToken}` } : {};

    Promise.all([
      fetch(`/api/v1/subscriptions/sync?userId=${userId}`, { method: 'POST', headers }),
      fetch(`/api/v1/user/sync?userId=${userId}`, { method: 'POST', headers })
    ])
    .then(() => {
      setIsSyncing(false);
      setIsSubscribedSyncing(false);
      loadAllChannels(email);
    })
    .catch((err) => {
      setIsSyncing(false);
      setIsSubscribedSyncing(false);
      console.error('Auto-sync error:', err);
    });
  };

  const triggerDemoLogin = () => {
    fetch('/api/v1/auth/demo-login', { method: 'POST' })
      .then((res) => res.json())
      .then((data) => {
        setUserToken(data.accessToken);
        alert(`Demo Mode Activated for ${activeUserEmail}!\nToken: ${data.accessToken}`);
        triggerFullSync(activeUserEmail);
      });
  };

  const syncSubscribedChannels = () => {
    setIsSubscribedSyncing(true);
    const userId = activeUserEmail.includes('2') ? 'user2' : 'user1';
    const headers = userToken ? { 'Authorization': `Bearer ${userToken}` } : {};

    fetch(`/api/v1/subscriptions/sync?userId=${userId}`, { method: 'POST', headers })
      .then((res) => res.json())
      .then((data) => {
        setIsSubscribedSyncing(false);
        alert(data.message || 'Subscribed channels sync completed!');
        loadAllChannels(activeUserEmail);
      })
      .catch((err) => {
        setIsSubscribedSyncing(false);
        console.error('Subscriptions sync error:', err);
      });
  };

  const syncYourContents = () => {
    setIsSyncing(true);
    const userId = activeUserEmail.includes('2') ? 'user2' : 'user1';
    const headers = userToken ? { 'Authorization': `Bearer ${userToken}` } : {};

    fetch(`/api/v1/user/sync?userId=${userId}`, { method: 'POST', headers })
      .then((res) => res.json())
      .then((data) => {
        setIsSyncing(false);
        alert(data.message || 'Sync completed!');
        loadAllChannels(activeUserEmail);
      })
      .catch((err) => {
        setIsSyncing(false);
        console.error('Sync error:', err);
      });
  };

  const selectSubscribedChannel = (channel, tab = 'videos') => {
    setSelectedSubscribedChannel(channel);
    setSubscribedTab(tab);

    let endpoint = `/api/v1/subscriptions/${channel.channelId}/videos`;
    if (tab === 'shorts') {
      endpoint = `/api/v1/subscriptions/${channel.channelId}/videos?shortsOnly=true`;
    } else if (tab === 'playlists') {
      endpoint = `/api/v1/subscriptions/${channel.channelId}/playlists`;
    }

    fetch(endpoint)
      .then((res) => res.json())
      .then((data) => setSubscribedContent(data))
      .catch((err) => console.error(`Failed to load subscribed ${tab}:`, err));
  };

  const loadYourContent = (tab = 'videos', email = activeUserEmail) => {
    setYourContentTab(tab);
    setIsYourContentLoading(true);
    const userId = email.includes('2') ? 'user2' : 'user1';
    let endpoint = `/api/v1/user/${tab}?userId=${userId}`;

    fetch(endpoint)
      .then((res) => res.json())
      .then((data) => {
        setYourContentData(Array.isArray(data) ? data : []);
        setIsYourContentLoading(false);
      })
      .catch((err) => {
        console.error(`Failed to load user ${tab}:`, err);
        setYourContentData([]);
        setIsYourContentLoading(false);
      });
  };

  const switchToUploadedTab = () => {
    setMainNavTab('uploaded');
    loadYourContent(yourContentTab, activeUserEmail);
  };

  const switchToSubscribedTab = () => {
    setMainNavTab('subscribed');
    if (selectedSubscribedChannel) {
      selectSubscribedChannel(selectedSubscribedChannel, subscribedTab);
    } else if (subscriptions.length > 0) {
      selectSubscribedChannel(subscriptions[0], 'videos');
    }
  };

  const handleAddCustomAccount = (e) => {
    e.preventDefault();
    if (customEmailInput.trim()) {
      switchUserAccount(customEmailInput.trim());
      setCustomEmailInput('');
    }
  };

  return (
    <div style={{ padding: '2rem', maxWidth: '1280px', margin: '0 auto', fontFamily: 'Inter, sans-serif' }}>
      {/* Header Bar with Real Google Email & Multi-User Account Switcher */}
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', borderBottom: '1px solid #27272a', paddingBottom: '1rem' }}>
        <div>
          <h1 style={{ margin: 0, fontSize: '2.2rem', color: '#ff0000', display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <span>▶</span> UTubeHub
          </h1>
          <p style={{ margin: '0.25rem 0 0 0', color: '#a1a1aa' }}>YouTube Subscriptions, Media Player & Multi-Account Intelligence Hub</p>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '0.5rem' }}>
          {/* User Account Switcher Selector with Actual Emails */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', background: '#18181b', padding: '0.4rem 0.8rem', borderRadius: '10px', border: '1px solid #3f3f46' }}>
            <span style={{ color: '#a1a1aa', fontSize: '0.8rem', fontWeight: 'bold' }}>👤 Account:</span>
            
            {googleUser && (
              <span style={{ background: '#059669', color: '#fff', padding: '0.3rem 0.75rem', borderRadius: '6px', fontSize: '0.8rem', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                ✓ {googleUser.email}
              </span>
            )}

            <button
              onClick={() => switchUserAccount('account1@gmail.com')}
              style={{
                background: activeUserEmail === 'account1@gmail.com' ? '#2563eb' : '#27272a',
                color: '#fff',
                border: 'none',
                padding: '0.3rem 0.75rem',
                borderRadius: '6px',
                fontSize: '0.8rem',
                fontWeight: 'bold',
                cursor: 'pointer'
              }}
            >
              account1@gmail.com
            </button>
            <button
              onClick={() => switchUserAccount('account2@gmail.com')}
              style={{
                background: activeUserEmail === 'account2@gmail.com' ? '#059669' : '#27272a',
                color: '#fff',
                border: 'none',
                padding: '0.3rem 0.75rem',
                borderRadius: '6px',
                fontSize: '0.8rem',
                fontWeight: 'bold',
                cursor: 'pointer'
              }}
            >
              account2@gmail.com
            </button>

            {/* Custom Email Input */}
            <form onSubmit={handleAddCustomAccount} style={{ display: 'flex', gap: '0.3rem' }}>
              <input
                type="email"
                placeholder="Enter google email..."
                value={customEmailInput}
                onChange={(e) => setCustomEmailInput(e.target.value)}
                style={{ background: '#09090b', color: '#fff', border: '1px solid #3f3f46', borderRadius: '4px', padding: '0.2rem 0.5rem', fontSize: '0.75rem', width: '150px' }}
              />
              <button type="submit" style={{ background: '#3b82f6', color: '#fff', border: 'none', borderRadius: '4px', padding: '0.2rem 0.5rem', fontSize: '0.75rem', cursor: 'pointer' }}>
                Switch
              </button>
            </form>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <div style={{ fontSize: '0.8rem', color: healthStatus.includes('Online') ? '#4ade80' : '#f87171' }}>
              ● {healthStatus}
            </div>
            <button
              onClick={triggerDemoLogin}
              style={{ color: '#ffffff', fontSize: '0.8rem', border: 'none', background: '#059669', padding: '0.4rem 0.75rem', borderRadius: '6px', fontWeight: 'bold', cursor: 'pointer' }}
            >
              🧪 Demo Auth
            </button>
            <a
              href="http://localhost:8080/oauth2/authorization/google"
              style={{ color: '#ffffff', fontSize: '0.8rem', textDecoration: 'none', background: '#2563eb', padding: '0.4rem 0.75rem', borderRadius: '6px', fontWeight: 'bold' }}
            >
              🔑 Log in with Google
            </a>
            <a
              href="/swagger-ui.html"
              target="_blank"
              rel="noreferrer"
              style={{ color: '#38bdf8', fontSize: '0.8rem', textDecoration: 'none', background: '#1e293b', padding: '0.4rem 0.75rem', borderRadius: '6px', border: '1px solid #334155' }}
            >
              📄 Swagger
            </a>
          </div>
        </div>
      </header>

      {/* Primary Navigation Tabs */}
      <nav style={{ display: 'flex', gap: '1rem', marginBottom: '2rem' }}>
        <button
          onClick={switchToSubscribedTab}
          style={{
            flex: 1,
            padding: '0.9rem 1.5rem',
            fontSize: '1.05rem',
            fontWeight: 'bold',
            borderRadius: '10px',
            border: 'none',
            background: mainNavTab === 'subscribed' ? '#cc0000' : '#18181b',
            color: '#ffffff',
            cursor: 'pointer',
            boxShadow: mainNavTab === 'subscribed' ? '0 4px 12px rgba(204, 0, 0, 0.4)' : 'none',
            transition: 'all 0.2s ease'
          }}
        >
          📺 Subscribed Channels ({subscriptions.length})
        </button>
        <button
          onClick={switchToUploadedTab}
          style={{
            flex: 1,
            padding: '0.9rem 1.5rem',
            fontSize: '1.05rem',
            fontWeight: 'bold',
            borderRadius: '10px',
            border: 'none',
            background: mainNavTab === 'uploaded' ? '#059669' : '#18181b',
            color: '#ffffff',
            cursor: 'pointer',
            boxShadow: mainNavTab === 'uploaded' ? '0 4px 12px rgba(5, 150, 105, 0.4)' : 'none',
            transition: 'all 0.2s ease'
          }}
        >
          👤 Your Contents {ownChannel ? `(${ownChannel.title})` : ''}
        </button>
      </nav>

      {/* AI Prompt Search Input */}
      <section style={{ marginBottom: '2rem', background: '#18181b', padding: '1.5rem', borderRadius: '12px', border: '1px solid #27272a' }}>
        <h3 style={{ marginTop: 0, color: '#f4f4f5', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          ✨ AI Prompt-Based Search Engine
        </h3>
        <p style={{ color: '#a1a1aa', fontSize: '0.9rem', marginBottom: '1rem' }}>
          Search across {mainNavTab === 'uploaded' ? 'your contents' : 'all subscribed channels'} for active account: <b>{activeUserEmail}</b>
        </p>
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <input
            type="text"
            placeholder="Enter your prompt query here..."
            value={searchPrompt}
            onChange={(e) => setSearchPrompt(e.target.value)}
            style={{
              flex: 1,
              padding: '0.8rem 1rem',
              borderRadius: '8px',
              border: '1px solid #3f3f46',
              background: '#09090b',
              color: '#fff',
              fontSize: '1rem'
            }}
          />
          <button
            onClick={() => alert(`AI Search for "${searchPrompt}" will process in Phase 4!`)}
            style={{
              padding: '0.8rem 1.75rem',
              borderRadius: '8px',
              border: 'none',
              background: '#cc0000',
              color: '#fff',
              fontWeight: 'bold',
              cursor: 'pointer'
            }}
          >
            Run AI Prompt
          </button>
        </div>
      </section>

      {/* Embedded In-Browser Video Player */}
      {activeVideoId && (
        <section style={{ marginBottom: '2rem', background: '#000', borderRadius: '12px', overflow: 'hidden', border: '1px solid #3f3f46' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#18181b', padding: '0.75rem 1rem' }}>
            <span style={{ color: '#fff', fontWeight: 'bold' }}>📺 Media Player</span>
            <button onClick={() => setActiveVideoId(null)} style={{ background: 'none', border: 'none', color: '#ef4444', fontSize: '1.2rem', cursor: 'pointer' }}>✖ Close Player</button>
          </div>
          <div style={{ position: 'relative', paddingBottom: '56.25%', height: 0 }}>
            <iframe
              src={`https://www.youtube.com/embed/${activeVideoId}?autoplay=1`}
              title="YouTube video player"
              frameBorder="0"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
              style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%' }}
            ></iframe>
          </div>
        </section>
      )}

      {/* PAGE VIEW 1: Subscribed Channels Page */}
      {mainNavTab === 'subscribed' && (
        <div style={{ display: 'grid', gridTemplateColumns: selectedSubscribedChannel ? '340px 1fr' : '1fr', gap: '2rem' }}>
          {/* Subscribed Channels Sidebar */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <h2 style={{ color: '#f4f4f5', margin: 0, fontSize: '1.25rem' }}>Subscribed Channels</h2>
              <button
                onClick={syncSubscribedChannels}
                disabled={isSubscribedSyncing}
                style={{ background: '#cc0000', color: '#fff', border: 'none', padding: '0.4rem 0.8rem', borderRadius: '6px', fontSize: '0.8rem', fontWeight: 'bold', cursor: 'pointer' }}
              >
                {isSubscribedSyncing ? '🔄 Syncing...' : '🔄 Sync Channels'}
              </button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {subscriptions.map((sub) => (
                <div
                  key={sub.channelId}
                  onClick={() => selectSubscribedChannel(sub, 'videos')}
                  style={{
                    background: selectedSubscribedChannel?.channelId === sub.channelId ? '#27272a' : '#18181b',
                    padding: '1rem',
                    borderRadius: '10px',
                    border: '1px solid #27272a',
                    cursor: 'pointer',
                    transition: 'all 0.2s ease'
                  }}
                >
                  <h4 style={{ margin: '0 0 0.4rem 0', color: '#ffffff' }}>{sub.title}</h4>
                  <p style={{ margin: 0, color: '#a1a1aa', fontSize: '0.85rem' }}>
                    {sub.videoCount || 0} videos • {(sub.subscriberCount || 0) > 1000000 ? ((sub.subscriberCount / 1000000).toFixed(1) + 'M subscribers') : (sub.subscriberCount + ' subscribers')}
                  </p>
                </div>
              ))}
            </div>
          </div>

          {/* Selected Subscribed Channel Content Inspector */}
          {selectedSubscribedChannel && (
            <div style={{ background: '#18181b', padding: '1.5rem', borderRadius: '12px', border: '1px solid #27272a' }}>
              <h2 style={{ margin: '0 0 0.5rem 0', color: '#ffffff' }}>{selectedSubscribedChannel.title}</h2>
              <p style={{ color: '#a1a1aa', fontSize: '0.9rem', marginBottom: '1.5rem' }}>{selectedSubscribedChannel.description}</p>

              {/* Sub-Tabs for Videos, Shorts, Playlists */}
              <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.5rem', borderBottom: '1px solid #27272a', paddingBottom: '0.75rem' }}>
                {['videos', 'shorts', 'playlists'].map((tab) => (
                  <button
                    key={tab}
                    onClick={() => selectSubscribedChannel(selectedSubscribedChannel, tab)}
                    style={{
                      padding: '0.5rem 1.25rem',
                      borderRadius: '6px',
                      border: 'none',
                      background: subscribedTab === tab ? '#cc0000' : '#27272a',
                      color: '#fff',
                      fontWeight: 'bold',
                      textTransform: 'capitalize',
                      cursor: 'pointer'
                    }}
                  >
                    {tab}
                  </button>
                ))}
              </div>

              {/* Subscribed Content Grid */}
              {subscribedContent.length === 0 ? (
                <p style={{ color: '#71717a' }}>No {subscribedTab} indexed yet for this channel.</p>
              ) : (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: '1rem' }}>
                  {subscribedContent.map((item, idx) => (
                    <div
                      key={idx}
                      onClick={() => item.videoId && setActiveVideoId(item.videoId)}
                      style={{ background: '#09090b', padding: '1rem', borderRadius: '8px', border: '1px solid #27272a', cursor: 'pointer' }}
                    >
                      <h5 style={{ margin: '0 0 0.5rem 0', color: '#fff' }}>{item.title}</h5>
                      <p style={{ margin: 0, color: '#a1a1aa', fontSize: '0.8rem' }}>▶ Play Video</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* PAGE VIEW 2: Your Contents Page (User Own Account) */}
      {mainNavTab === 'uploaded' && (
        <div style={{ background: '#18181b', padding: '1.5rem', borderRadius: '12px', border: '1px solid #059669' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
            <div>
              <h2 style={{ margin: '0 0 0.4rem 0', color: '#ffffff', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                👤 Your Contents {ownChannel ? `(${ownChannel.title})` : ''}
              </h2>
              <p style={{ margin: 0, color: '#a1a1aa', fontSize: '0.9rem' }}>
                {ownChannel ? ownChannel.description : 'Your uploaded videos, playlists, live streams, and community posts'}
              </p>
            </div>
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
              <button
                onClick={syncYourContents}
                disabled={isSyncing}
                style={{ background: '#059669', color: '#fff', border: 'none', padding: '0.5rem 1rem', borderRadius: '6px', fontWeight: 'bold', cursor: 'pointer' }}
              >
                {isSyncing ? '🔄 Syncing...' : '🔄 Sync Your Contents'}
              </button>
              <span style={{ background: '#059669', color: '#fff', fontSize: '0.8rem', padding: '0.3rem 0.75rem', borderRadius: '6px', fontWeight: 'bold' }}>
                ACTIVE: {activeUserEmail}
              </span>
            </div>
          </div>

          {/* Sub-Tabs for Videos, Playlists, Live, Posts */}
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.5rem', borderBottom: '1px solid #27272a', paddingBottom: '0.75rem' }}>
            {['videos', 'playlists', 'live', 'posts'].map((tab) => (
              <button
                key={tab}
                onClick={() => loadYourContent(tab, activeUserEmail)}
                style={{
                  padding: '0.5rem 1.25rem',
                  borderRadius: '6px',
                  border: 'none',
                  background: yourContentTab === tab ? '#059669' : '#27272a',
                  color: '#fff',
                  fontWeight: 'bold',
                  textTransform: 'capitalize',
                  cursor: 'pointer'
                }}
              >
                {tab}
              </button>
            ))}
          </div>

          {/* Your Contents Grid */}
          {isYourContentLoading ? (
            <p style={{ color: '#4ade80' }}>⏳ Loading your {yourContentTab}...</p>
          ) : yourContentData.length === 0 ? (
            <p style={{ color: '#71717a' }}>No {yourContentTab} found for active account {activeUserEmail}. Click "🔄 Sync Your Contents" or Log in with Google.</p>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))', gap: '1.25rem' }}>
              {yourContentData.map((item, idx) => (
                <div
                  key={idx}
                  onClick={() => item.videoId && setActiveVideoId(item.videoId)}
                  style={{ background: '#09090b', padding: '1.2rem', borderRadius: '10px', border: '1px solid #27272a', cursor: 'pointer' }}
                >
                  <h4 style={{ margin: '0 0 0.5rem 0', color: '#fff', fontSize: '1rem' }}>{item.title || item.content}</h4>
                  <p style={{ margin: 0, color: '#a1a1aa', fontSize: '0.85rem' }}>
                    {item.videoId ? '▶ Play Video' : (item.status ? `Stream Status: ${item.status}` : 'Community Update')}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
