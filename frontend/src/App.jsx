import React, { useState, useEffect } from 'react';

export default function App() {
  const [healthStatus, setHealthStatus] = useState('Connecting...');
  const [activeUserEmail, setActiveUserEmail] = useState('atrteach@gmail.com');
  const [googleUser, setGoogleUser] = useState(null);
  const [authenticatedAccounts, setAuthenticatedAccounts] = useState([]);
  const [userToken, setUserToken] = useState(null);

  const [subscriptions, setSubscriptions] = useState([]);
  const [ownChannel, setOwnChannel] = useState(null);
  const [mainNavTab, setMainNavTab] = useState('subscribed'); // 'subscribed' | 'uploaded'
  
  // Subscribed channels state
  const [selectedSubscribedChannel, setSelectedSubscribedChannel] = useState(null);
  const [subscribedTab, setSubscribedTab] = useState('videos'); // 'videos' | 'shorts' | 'playlists' | 'live' | 'podcasts' | 'posts'
  const [subscribedContent, setSubscribedContent] = useState([]);
  const [subscribedCounts, setSubscribedCounts] = useState({ videos: 0, shorts: 0, playlists: 0, live: 0, podcasts: 0, posts: 0 });
  const [isSubscribedSyncing, setIsSubscribedSyncing] = useState(false);

  // Your Contents state
  const [yourContentTab, setYourContentTab] = useState('videos'); // 'videos' | 'playlists' | 'live' | 'posts' | 'courses' | 'clips'
  const [yourContentData, setYourContentData] = useState([]);
  const [yourContentCounts, setYourContentCounts] = useState({ videos: 0, playlists: 0, live: 0, posts: 0, courses: 0, clips: 0 });
  const [isYourContentLoading, setIsYourContentLoading] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);

  const [activeVideoId, setActiveVideoId] = useState(null);
  const [searchPrompt, setSearchPrompt] = useState('');

  const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');

  useEffect(() => {
    // Check Backend Health Status
    fetch(`${API_BASE}/v1/health`)
      .then((res) => res.json())
      .then((data) => setHealthStatus(`Backend Online (${data.service} v${data.version})`))
      .catch(() => setHealthStatus('Backend Offline / Reconnecting...'));

    // Check Google Auth Status
    fetch(`${API_BASE}/v1/auth/user`, { credentials: 'include' })
      .then((res) => res.json())
      .then((data) => {
        if (data.authenticated && data.email) {
          setGoogleUser(data);
          setActiveUserEmail(data.email);
          if (data.accounts && Array.isArray(data.accounts)) {
            setAuthenticatedAccounts(data.accounts);
          } else {
            setAuthenticatedAccounts([{ email: data.email, name: data.name }]);
          }
          
          fetch(`${API_BASE}/v1/auth/token`, { credentials: 'include' })
            .then((r) => r.json())
            .then((tokData) => {
              if (tokData.accessToken) setUserToken(tokData.accessToken);
            })
            .catch(() => {});

          loadAllChannels(data.email);
        } else {
          setActiveUserEmail('atrteach@gmail.com');
          setAuthenticatedAccounts([{ email: 'atrteach@gmail.com', name: 'Google User' }]);
          loadAllChannels('atrteach@gmail.com');
        }
      })
      .catch(() => {
        setActiveUserEmail('atrteach@gmail.com');
        loadAllChannels('atrteach@gmail.com');
      });
  }, []);

  const switchUserAccount = (email) => {
    setActiveUserEmail(email);
    loadAllChannels(email).then(() => {
      triggerFullSync(email);
    });
  };

  const getBackendUrl = (path) => {
    const baseUrl = import.meta.env.VITE_API_BASE_URL
      ? import.meta.env.VITE_API_BASE_URL.replace(/\/api\/?$/, '')
      : 'http://localhost:8080';
    return `${baseUrl}${path}`;
  };

  const handleDropdownChange = (e) => {
    const val = e.target.value;
    if (val === 'LOGIN_GOOGLE_ACCOUNT') {
      window.location.href = getBackendUrl('/oauth2/authorization/google');
    } else {
      switchUserAccount(val);
    }
  };

  const loadAllChannels = (email = activeUserEmail) => {
    return fetch(`${API_BASE}/v1/subscriptions?userId=${encodeURIComponent(email)}`, { credentials: 'include' })
      .then((res) => res.json())
      .then((data) => {
        const mine = data.find((ch) => ch.isMine);
        const subbed = data.filter((ch) => !ch.isMine);

        setOwnChannel(mine || null);
        setSubscriptions(subbed);

        if (subbed.length > 0) {
          selectSubscribedChannel(subbed[0], 'videos', email);
        } else {
          setSelectedSubscribedChannel(null);
          setSubscribedContent([]);
          setSubscribedCounts({ videos: 0, shorts: 0, playlists: 0, live: 0, podcasts: 0, posts: 0 });
        }

        fetchAllYourContentCounts(email);
        loadYourContent('videos', email);
        return { mine, subbed };
      })
      .catch((err) => console.error('Failed to load channels:', err));
  };

  const fetchSubscribedChannelCounts = (channelId, email = activeUserEmail) => {
    const encEmail = encodeURIComponent(email);
    Promise.all([
      fetch(`${API_BASE}/v1/subscriptions/${channelId}/videos?userId=${encEmail}`).then((r) => r.json()),
      fetch(`${API_BASE}/v1/subscriptions/${channelId}/videos?userId=${encEmail}&shortsOnly=true`).then((r) => r.json()),
      fetch(`${API_BASE}/v1/subscriptions/${channelId}/playlists?userId=${encEmail}`).then((r) => r.json()),
      fetch(`${API_BASE}/v1/subscriptions/${channelId}/live?userId=${encEmail}`).then((r) => r.json()),
      fetch(`${API_BASE}/v1/subscriptions/${channelId}/podcasts?userId=${encEmail}`).then((r) => r.json()),
      fetch(`${API_BASE}/v1/subscriptions/${channelId}/posts?userId=${encEmail}`).then((r) => r.json())
    ])
    .then(([vids, shorts, lists, live, podcasts, posts]) => {
      setSubscribedCounts({
        videos: Array.isArray(vids) ? vids.length : 0,
        shorts: Array.isArray(shorts) ? shorts.length : 0,
        playlists: Array.isArray(lists) ? lists.length : 0,
        live: Array.isArray(live) ? live.length : 0,
        podcasts: Array.isArray(podcasts) ? podcasts.length : 0,
        posts: Array.isArray(posts) ? posts.length : 0
      });
    })
    .catch((err) => console.error('Error fetching subscribed counts:', err));
  };

  const fetchAllYourContentCounts = (email = activeUserEmail) => {
    const encEmail = encodeURIComponent(email);
    Promise.all([
      fetch(`${API_BASE}/v1/user/videos?userId=${encEmail}`).then((r) => r.json()),
      fetch(`${API_BASE}/v1/user/playlists?userId=${encEmail}`).then((r) => r.json()),
      fetch(`${API_BASE}/v1/user/live?userId=${encEmail}`).then((r) => r.json()),
      fetch(`${API_BASE}/v1/user/posts?userId=${encEmail}`).then((r) => r.json()),
      fetch(`${API_BASE}/v1/user/courses?userId=${encEmail}`).then((r) => r.json()),
      fetch(`${API_BASE}/v1/user/clips?userId=${encEmail}`).then((r) => r.json())
    ])
    .then(([vids, lists, live, posts, courses, clips]) => {
      setYourContentCounts({
        videos: Array.isArray(vids) ? vids.length : 0,
        playlists: Array.isArray(lists) ? lists.length : 0,
        live: Array.isArray(live) ? live.length : 0,
        posts: Array.isArray(posts) ? posts.length : 0,
        courses: Array.isArray(courses) ? courses.length : 0,
        clips: Array.isArray(clips) ? clips.length : 0
      });
    })
    .catch((err) => console.error('Error fetching user content counts:', err));
  };

  const triggerFullSync = (email = activeUserEmail) => {
    setIsSyncing(true);
    setIsSubscribedSyncing(true);
    const headers = userToken ? { 'Authorization': `Bearer ${userToken}` } : {};

    Promise.all([
      fetch(`${API_BASE}/v1/subscriptions/sync?userId=${encodeURIComponent(email)}`, { method: 'POST', headers, credentials: 'include' }),
      fetch(`${API_BASE}/v1/user/sync?userId=${encodeURIComponent(email)}`, { method: 'POST', headers, credentials: 'include' })
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
    fetch(`${API_BASE}/v1/auth/demo-login`, { method: 'POST', credentials: 'include' })
      .then((res) => res.json())
      .then((data) => {
        setUserToken(data.accessToken);
        alert(`Demo Mode Activated for ${activeUserEmail}!\nToken: ${data.accessToken}`);
        triggerFullSync(activeUserEmail);
      });
  };

  const syncSubscribedChannels = () => {
    setIsSubscribedSyncing(true);
    const headers = userToken ? { 'Authorization': `Bearer ${userToken}` } : {};

    fetch(`${API_BASE}/v1/subscriptions/sync?userId=${encodeURIComponent(activeUserEmail)}`, { method: 'POST', headers, credentials: 'include' })
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
    const headers = userToken ? { 'Authorization': `Bearer ${userToken}` } : {};

    fetch(`${API_BASE}/v1/user/sync?userId=${encodeURIComponent(activeUserEmail)}`, { method: 'POST', headers, credentials: 'include' })
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

  const selectSubscribedChannel = (channel, tab = 'videos', email = activeUserEmail) => {
    setSelectedSubscribedChannel(channel);
    setSubscribedTab(tab);
    fetchSubscribedChannelCounts(channel.channelId, email);

    let endpoint = `${API_BASE}/v1/subscriptions/${channel.channelId}/videos?userId=${encodeURIComponent(email)}`;
    if (tab === 'shorts') {
      endpoint = `${API_BASE}/v1/subscriptions/${channel.channelId}/videos?userId=${encodeURIComponent(email)}&shortsOnly=true`;
    } else if (tab === 'playlists') {
      endpoint = `${API_BASE}/v1/subscriptions/${channel.channelId}/playlists?userId=${encodeURIComponent(email)}`;
    } else if (tab === 'live') {
      endpoint = `${API_BASE}/v1/subscriptions/${channel.channelId}/live?userId=${encodeURIComponent(email)}`;
    } else if (tab === 'podcasts') {
      endpoint = `${API_BASE}/v1/subscriptions/${channel.channelId}/podcasts?userId=${encodeURIComponent(email)}`;
    } else if (tab === 'posts') {
      endpoint = `${API_BASE}/v1/subscriptions/${channel.channelId}/posts?userId=${encodeURIComponent(email)}`;
    }

    fetch(endpoint)
      .then((res) => res.json())
      .then((data) => setSubscribedContent(Array.isArray(data) ? data : []))
      .catch((err) => console.error(`Failed to load subscribed ${tab}:`, err));
  };

  const loadYourContent = (tab = 'videos', email = activeUserEmail) => {
    setYourContentTab(tab);
    setIsYourContentLoading(true);
    let endpoint = `${API_BASE}/v1/user/${tab}?userId=${encodeURIComponent(email)}`;

    fetch(endpoint)
      .then((res) => res.json())
      .then((data) => {
        const arr = Array.isArray(data) ? data : [];
        setYourContentData(arr);
        setYourContentCounts((prev) => ({ ...prev, [tab]: arr.length }));
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
    fetchAllYourContentCounts(activeUserEmail);
    loadYourContent(yourContentTab, activeUserEmail);
  };

  const switchToSubscribedTab = () => {
    setMainNavTab('subscribed');
    if (selectedSubscribedChannel) {
      selectSubscribedChannel(selectedSubscribedChannel, subscribedTab, activeUserEmail);
    } else if (subscriptions.length > 0) {
      selectSubscribedChannel(subscriptions[0], 'videos', activeUserEmail);
    }
  };

  return (
    <div style={{ padding: '2rem', maxWidth: '1280px', margin: '0 auto', fontFamily: 'Inter, sans-serif' }}>
      {/* Header Bar with Real Google OAuth Account Selector */}
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', borderBottom: '1px solid #27272a', paddingBottom: '1rem' }}>
        <div>
          <h1 style={{ margin: 0, fontSize: '2.2rem', color: '#ff0000', display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <span>▶</span> UTubeHub
          </h1>
          <p style={{ margin: '0.25rem 0 0 0', color: '#a1a1aa' }}>YouTube Subscriptions, Media Player & Multi-Account Intelligence Hub</p>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '0.5rem' }}>
          {/* Real Google Account Pulldown Selector */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', background: '#18181b', padding: '0.4rem 0.8rem', borderRadius: '10px', border: '1px solid #3f3f46' }}>
            <span style={{ color: '#a1a1aa', fontSize: '0.85rem', fontWeight: 'bold' }}>👤 Google Account:</span>
            
            <select
              value={activeUserEmail}
              onChange={handleDropdownChange}
              style={{
                background: '#09090b',
                color: '#ffffff',
                border: '1px solid #3f3f46',
                borderRadius: '6px',
                padding: '0.4rem 0.8rem',
                fontSize: '0.85rem',
                fontWeight: 'bold',
                cursor: 'pointer'
              }}
            >
              {authenticatedAccounts.map((acc) => (
                <option key={acc.email} value={acc.email}>
                  ✓ {acc.email}
                </option>
              ))}
              {!authenticatedAccounts.some((acc) => acc.email === 'atrteach@gmail.com') && (
                <option value="atrteach@gmail.com">atrteach@gmail.com</option>
              )}
              <option value="LOGIN_GOOGLE_ACCOUNT">+ Log in / Switch another Google Account...</option>
            </select>
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
              href={getBackendUrl('/oauth2/authorization/google')}
              style={{ color: '#ffffff', fontSize: '0.8rem', textDecoration: 'none', background: '#2563eb', padding: '0.4rem 0.75rem', borderRadius: '6px', fontWeight: 'bold' }}
            >
              🔑 Log in with Google
            </a>
            <a
              href={getBackendUrl('/swagger-ui.html')}
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
          Search across {mainNavTab === 'uploaded' ? 'your contents' : 'all subscribed channels'} for active Google account: <b>{activeUserEmail}</b>
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
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
                <h2 style={{ color: '#f4f4f5', margin: 0, fontSize: '1.25rem' }}>Subscribed Channels</h2>
                <span style={{ color: '#4ade80', fontSize: '0.8rem', fontWeight: 'bold' }}>
                  👤 Active Account: {activeUserEmail}
                </span>
              </div>
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
                  onClick={() => selectSubscribedChannel(sub, 'videos', activeUserEmail)}
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

              {/* 6 Sub-Tabs: Videos, Shorts, Playlists, Live, Podcasts, Posts with Item Counts */}
              <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap', marginBottom: '1.5rem', borderBottom: '1px solid #27272a', paddingBottom: '0.75rem' }}>
                <button
                  onClick={() => selectSubscribedChannel(selectedSubscribedChannel, 'videos', activeUserEmail)}
                  style={{
                    padding: '0.5rem 1rem',
                    borderRadius: '6px',
                    border: 'none',
                    background: subscribedTab === 'videos' ? '#cc0000' : '#27272a',
                    color: '#fff',
                    fontWeight: 'bold',
                    cursor: 'pointer'
                  }}
                >
                  📹 Videos ({subscribedCounts.videos})
                </button>
                <button
                  onClick={() => selectSubscribedChannel(selectedSubscribedChannel, 'shorts', activeUserEmail)}
                  style={{
                    padding: '0.5rem 1rem',
                    borderRadius: '6px',
                    border: 'none',
                    background: subscribedTab === 'shorts' ? '#cc0000' : '#27272a',
                    color: '#fff',
                    fontWeight: 'bold',
                    cursor: 'pointer'
                  }}
                >
                  ⚡ Shorts ({subscribedCounts.shorts})
                </button>
                <button
                  onClick={() => selectSubscribedChannel(selectedSubscribedChannel, 'playlists', activeUserEmail)}
                  style={{
                    padding: '0.5rem 1rem',
                    borderRadius: '6px',
                    border: 'none',
                    background: subscribedTab === 'playlists' ? '#cc0000' : '#27272a',
                    color: '#fff',
                    fontWeight: 'bold',
                    cursor: 'pointer'
                  }}
                >
                  📑 Playlists ({subscribedCounts.playlists})
                </button>
                <button
                  onClick={() => selectSubscribedChannel(selectedSubscribedChannel, 'live', activeUserEmail)}
                  style={{
                    padding: '0.5rem 1rem',
                    borderRadius: '6px',
                    border: 'none',
                    background: subscribedTab === 'live' ? '#cc0000' : '#27272a',
                    color: '#fff',
                    fontWeight: 'bold',
                    cursor: 'pointer'
                  }}
                >
                  🔴 Live ({subscribedCounts.live})
                </button>
                <button
                  onClick={() => selectSubscribedChannel(selectedSubscribedChannel, 'podcasts', activeUserEmail)}
                  style={{
                    padding: '0.5rem 1rem',
                    borderRadius: '6px',
                    border: 'none',
                    background: subscribedTab === 'podcasts' ? '#cc0000' : '#27272a',
                    color: '#fff',
                    fontWeight: 'bold',
                    cursor: 'pointer'
                  }}
                >
                  🎙️ Podcasts ({subscribedCounts.podcasts})
                </button>
                <button
                  onClick={() => selectSubscribedChannel(selectedSubscribedChannel, 'posts', activeUserEmail)}
                  style={{
                    padding: '0.5rem 1rem',
                    borderRadius: '6px',
                    border: 'none',
                    background: subscribedTab === 'posts' ? '#cc0000' : '#27272a',
                    color: '#fff',
                    fontWeight: 'bold',
                    cursor: 'pointer'
                  }}
                >
                  💬 Posts ({subscribedCounts.posts})
                </button>
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
                      <h5 style={{ margin: '0 0 0.5rem 0', color: '#fff' }}>{item.title || item.content}</h5>
                      <p style={{ margin: 0, color: '#a1a1aa', fontSize: '0.8rem' }}>
                        {item.videoId ? '▶ Play Video' : (item.episodeCount ? `${item.episodeCount} Episodes` : 'Community Update')}
                      </p>
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
                {ownChannel ? ownChannel.description : 'Your uploaded videos, playlists, live streams, community posts, courses, and clips'}
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

          {/* 6 Sub-Tabs for Videos, Playlists, Live, Posts, Courses, Clips with Live Item Counts */}
          <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap', marginBottom: '1.5rem', borderBottom: '1px solid #27272a', paddingBottom: '0.75rem' }}>
            <button
              onClick={() => loadYourContent('videos', activeUserEmail)}
              style={{
                padding: '0.5rem 1rem',
                borderRadius: '6px',
                border: 'none',
                background: yourContentTab === 'videos' ? '#059669' : '#27272a',
                color: '#fff',
                fontWeight: 'bold',
                cursor: 'pointer'
              }}
            >
              📹 Videos ({yourContentCounts.videos})
            </button>
            <button
              onClick={() => loadYourContent('playlists', activeUserEmail)}
              style={{
                padding: '0.5rem 1rem',
                borderRadius: '6px',
                border: 'none',
                background: yourContentTab === 'playlists' ? '#059669' : '#27272a',
                color: '#fff',
                fontWeight: 'bold',
                cursor: 'pointer'
              }}
            >
              📑 Playlists ({yourContentCounts.playlists})
            </button>
            <button
              onClick={() => loadYourContent('live', activeUserEmail)}
              style={{
                padding: '0.5rem 1rem',
                borderRadius: '6px',
                border: 'none',
                background: yourContentTab === 'live' ? '#059669' : '#27272a',
                color: '#fff',
                fontWeight: 'bold',
                cursor: 'pointer'
              }}
            >
              🔴 Live ({yourContentCounts.live})
            </button>
            <button
              onClick={() => loadYourContent('posts', activeUserEmail)}
              style={{
                padding: '0.5rem 1rem',
                borderRadius: '6px',
                border: 'none',
                background: yourContentTab === 'posts' ? '#059669' : '#27272a',
                color: '#fff',
                fontWeight: 'bold',
                cursor: 'pointer'
              }}
            >
              💬 Posts ({yourContentCounts.posts})
            </button>
            <button
              onClick={() => loadYourContent('courses', activeUserEmail)}
              style={{
                padding: '0.5rem 1rem',
                borderRadius: '6px',
                border: 'none',
                background: yourContentTab === 'courses' ? '#059669' : '#27272a',
                color: '#fff',
                fontWeight: 'bold',
                cursor: 'pointer'
              }}
            >
              🎓 Courses ({yourContentCounts.courses})
            </button>
            <button
              onClick={() => loadYourContent('clips', activeUserEmail)}
              style={{
                padding: '0.5rem 1rem',
                borderRadius: '6px',
                border: 'none',
                background: yourContentTab === 'clips' ? '#059669' : '#27272a',
                color: '#fff',
                fontWeight: 'bold',
                cursor: 'pointer'
              }}
            >
              ✂️ Clips ({yourContentCounts.clips})
            </button>
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
                    {item.videoId ? '▶ Play Video' : (item.lessonCount ? `${item.lessonCount} Lessons` : (item.status ? `Stream Status: ${item.status}` : 'Community Update'))}
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
