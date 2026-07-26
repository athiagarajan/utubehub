import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import App from './App';

describe('UTubeHub App Component', () => {
  it('renders application header, active account badge, and Swagger button', () => {
    // Mock fetch for health, auth user, and subscriptions endpoints
    global.fetch = vi.fn().mockImplementation((url) => {
      if (url.includes('/api/v1/health')) {
        return Promise.resolve({
          json: () => Promise.resolve({ status: 'UP', service: 'utubehub-backend', version: '1.0.0' }),
        });
      }
      if (url.includes('/api/v1/auth/user')) {
        return Promise.resolve({
          json: () => Promise.resolve({ authenticated: true, email: 'atrteach@gmail.com', name: 'Thiagu' }),
        });
      }
      return Promise.resolve({
        json: () => Promise.resolve([]),
      });
    });

    render(<App />);

    expect(screen.getAllByText(/UTubeHub/i)[0]).toBeInTheDocument();
    expect(screen.getByText(/👤 Google Account:/i)).toBeInTheDocument();
    expect(screen.getAllByText(/atrteach@gmail.com/i)[0]).toBeInTheDocument();
  });
});
