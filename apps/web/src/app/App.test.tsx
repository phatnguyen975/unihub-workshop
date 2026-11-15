import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect } from 'vitest';
import App from './App';
describe('access application', () => {
    it('renders real visitor entry and recovers unknown routes', async () => { window.history.replaceState({}, '', '/missing'); render(<App />); expect(await screen.findByRole('heading', { name: /find that page/ })).toBeVisible(); await userEvent.click(screen.getByRole('link', { name: 'Back to home' })); expect(await screen.findByRole('heading', { name: 'Your next step starts here.' })).toBeVisible(); });
    it('shows a common accessible form and validates before login', async () => { window.history.replaceState({}, '', '/login'); render(<App />); await screen.findByRole('heading', { name: 'Welcome back' }); await userEvent.click(screen.getByRole('button', { name: 'Sign in' })); expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true'); expect(screen.getByLabelText('Password', { exact: true })).toBeVisible(); });
});
