import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import App from './App';

describe('UTubeHub App Component', () => {
  it('renders application header and title', () => {
    // Mock fetch for health and subscriptions endpoints
    global.fetch = vi.fn().mockImplementation((url) => {
      if (url.includes('/api/v1/health')) {
        return Promise.resolve({
          json: () => Promise.resolve({ status: 'UP', service: 'utubehub-backend', version: '1.0.0' }),
        });
      }
      return Promise.resolve({
        json: () => Promise.resolve([]),
      });
    });

    render(<App />);

    expect(screen.getByText(/UTubeHub/i)).toBeInTheDocument();
    expect(screen.getAllByText(/Subscribed Channels/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/Your Contents/i)).toBeInTheDocument();
  });
});
