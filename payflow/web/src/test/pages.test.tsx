import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api'
import { AdminReviews, Dashboard, Pay, Reconciliation, Status, Transactions } from '../pages'

function renderPage(component: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter>{component}</MemoryRouter></QueryClientProvider>)
}

afterEach(() => vi.restoreAllMocks())

describe('dashboard', () => {
  it('renders wallet summary and recent transactions', async () => {
    renderPage(<Dashboard />)
    expect(await screen.findByText('$12,684.00')).toBeInTheDocument()
    expect(screen.getByText('Northstar Mobility')).toBeInTheDocument()
  })
})

describe('payment flow', () => {
  it('submits a payment using minor units', async () => {
    const user = userEvent.setup()
    renderPage(<Pay />)
    await user.type(screen.getByLabelText('Payment amount'), '42.50')
    await user.type(screen.getByPlaceholderText('Who are you paying?'), 'Lumen Market')
    expect(screen.getByText('Sent as 4250 minor units')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /review & authorize/i }))
    expect(await screen.findByText('Payment initiated')).toBeInTheDocument()
    expect(screen.getByText('to Lumen Market')).toBeInTheDocument()
  })

  it('keeps the form and reports an authorization failure', async () => {
    vi.spyOn(api, 'createPayment').mockRejectedValueOnce(new Error('Payment service unavailable'))
    const user = userEvent.setup()
    renderPage(<Pay />)
    await user.type(screen.getByLabelText('Payment amount'), '10.00')
    await user.type(screen.getByPlaceholderText('Who are you paying?'), 'Lumen Market')
    await user.click(screen.getByRole('button', { name: /review & authorize/i }))

    expect(await screen.findByText('Payment service unavailable')).toBeInTheDocument()
    expect(screen.getByLabelText('Payment amount')).toHaveValue('10.00')
  })
})

describe('transactions', () => {
  it('filters transactions by merchant', async () => {
    const user = userEvent.setup()
    renderPage(<Transactions />)
    expect(await screen.findByText('Northstar Mobility')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Search transactions'), 'Arc')
    expect(await screen.findByText('Arc Coffee Club')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('Northstar Mobility')).not.toBeInTheDocument())
  })
})

describe('admin operations', () => {
  it('renders review evidence and approves a held payment', async () => {
    const user = userEvent.setup()
    const decide = vi.spyOn(api, 'decideReview').mockResolvedValueOnce(undefined)
    renderPage(<AdminReviews />)

    expect(await screen.findByText('Paper & Pine')).toBeInTheDocument()
    expect(screen.getByText('New device fingerprint')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /approve/i }))

    expect(await screen.findByRole('status')).toHaveTextContent('Payment approved')
    expect(decide).toHaveBeenCalledWith('pay_2', 'approve')
  })

  it('shows reconciliation mismatches and degraded service state', async () => {
    const first = renderPage(<Reconciliation />)
    expect(await screen.findByText('rec_01')).toBeInTheDocument()
    expect(screen.getByText('Settlement integrity')).toBeInTheDocument()
    expect(screen.getByText('pay_4982')).toBeInTheDocument()
    first.unmount()

    renderPage(<Status />)
    expect(await screen.findByText('All core systems operational')).toBeInTheDocument()
    expect(screen.getByText('Fraud intelligence')).toBeInTheDocument()
    expect(screen.getByText('DEGRADED')).toBeInTheDocument()
  })
})
