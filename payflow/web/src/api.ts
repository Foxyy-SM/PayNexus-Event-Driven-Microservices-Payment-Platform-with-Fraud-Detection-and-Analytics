import type {
  DashboardSummary, FraudReview, LedgerEntry, Notification, Page, Payment, PaymentRequest,
  PaymentStatus, ReconciliationMismatch, RiskAnalysis, SystemStatus, Transaction, User, Wallet,
} from './types'

const demoMode = (import.meta.env.VITE_DEMO_MODE ?? 'false') === 'true'
let tokenProvider: () => string | undefined = () => undefined
export const setTokenProvider = (provider: () => string | undefined) => { tokenProvider = provider }

const now = new Date()
const iso = (days = 0) => new Date(now.getTime() - days * 86_400_000).toISOString()
const transactions: Transaction[] = [
  { id: 'txn_1', paymentId: 'pay_1', merchant: 'Northstar Mobility', amountMinor: 8420, currency: 'USD', status: 'CAPTURED', riskLevel: 'LOW', createdAt: iso() },
  { id: 'txn_2', paymentId: 'pay_2', merchant: 'Paper & Pine', amountMinor: 12450, currency: 'USD', status: 'RISK_REVIEW', riskLevel: 'HIGH', createdAt: iso(1) },
  { id: 'txn_3', paymentId: 'pay_3', merchant: 'Arc Coffee Club', amountMinor: 1899, currency: 'USD', status: 'CAPTURED', riskLevel: 'LOW', createdAt: iso(2) },
  { id: 'txn_4', paymentId: 'pay_4', merchant: 'Signal Airlines', amountMinor: 42800, currency: 'USD', status: 'AUTHORIZED', riskLevel: 'MEDIUM', createdAt: iso(4) },
  { id: 'txn_5', paymentId: 'pay_5', merchant: 'Halo Energy', amountMinor: 9325, currency: 'USD', status: 'FAILED', riskLevel: 'LOW', createdAt: iso(6) },
]

const wallet: Wallet = { id: 'wal_01', availableMinor: 1268400, reservedMinor: 12450, currency: 'USD', updatedAt: iso() }
const notifications: Notification[] = [
  { id: 'n1', title: 'Payment needs review', message: 'Paper & Pine was held for an additional security check.', type: 'SECURITY', read: false, createdAt: iso() },
  { id: 'n2', title: 'Transfer complete', message: '$84.20 was paid to Northstar Mobility.', type: 'PAYMENT', read: false, createdAt: iso() },
  { id: 'n3', title: 'All systems healthy', message: 'Scheduled payment infrastructure maintenance is complete.', type: 'SYSTEM', read: true, createdAt: iso(2) },
]

const demo = {
  user: { id: 'usr_01', email: 'avery@paynexus.dev', firstName: 'Avery', lastName: 'Stone', roles: ['user', 'admin'], kycStatus: 'VERIFIED', createdAt: iso(220) } satisfies User,
  wallet,
  ledger: [
    { id: 'l1', type: 'DEBIT', amountMinor: 8420, currency: 'USD', description: 'Northstar Mobility', balanceAfterMinor: 1268400, createdAt: iso() },
    { id: 'l2', type: 'RESERVE', amountMinor: 12450, currency: 'USD', description: 'Paper & Pine authorization', balanceAfterMinor: 1276820, createdAt: iso(1) },
    { id: 'l3', type: 'CREDIT', amountMinor: 250000, currency: 'USD', description: 'Wallet top up', balanceAfterMinor: 1289270, createdAt: iso(3) },
  ] satisfies LedgerEntry[],
  reviews: [{ paymentId: 'pay_2', transaction: transactions[1], riskScore: 82, reasons: ['New device fingerprint', 'Unusual purchase velocity'], state: 'PENDING', queuedAt: iso(1) }] satisfies FraudReview[],
  mismatches: [{ id: 'rec_01', paymentId: 'pay_4982', mismatchType: 'COMPLETED_CAPTURE_MISSING_OR_WRONG', expectedEvidence: 'CAPTURE amountMinor=28750', actualEvidence: 'CAPTURE amountMinor=28570', repairAction: 'NONE', repairStatus: 'NOT_APPLICABLE', detectedAt: iso() }] satisfies ReconciliationMismatch[],
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(tokenProvider() ? { Authorization: `Bearer ${tokenProvider()}` } : {}), ...init?.headers },
  })
  if (!response.ok) throw new Error((await response.text()) || `Request failed (${response.status})`)
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

const wait = <T>(value: T) => new Promise<T>((resolve) => window.setTimeout(() => resolve(value), 220))
const pageOf = <T>(content: T[], page = 0, size = 10): Page<T> => ({
  content: content.slice(page * size, (page + 1) * size), page, size, totalElements: content.length, totalPages: Math.ceil(content.length / size),
})

type RawUser = { id: string; email: string; fullName: string; role: string; kycStatus: User['kycStatus']; createdAt: string }
type RawWallet = { walletId: string; availableBalanceMinor: number; reservedBalanceMinor: number; currency: Wallet['currency']; version: number }
type RawLedger = { id: string; entryType: LedgerEntry['type']; amountMinor: number; availableBalanceAfterMinor: number; createdAt: string }
type RawPayment = { paymentId: string; amountMinor: number; currency: Payment['currency']; merchantId: string; status: PaymentStatus; riskScore?: number; fraudDecision?: string; createdAt: string; completedAt?: string }
type RawTransaction = { paymentId: string; amountMinor: number; currency: Transaction['currency']; merchantId: string; status: PaymentStatus; createdAt: string }
type RawPage<T> = { content: T[]; number: number; size: number; totalElements: number; totalPages: number }
type RawRisk = { paymentId: string; riskScore: number; decision: string; explanation: string; triggeredRules: string }
type RawNotification = { id: string; template: string; status: string; payloadJson?: string; createdAt: string }
type RawMismatch = ReconciliationMismatch

const toWallet = (raw: RawWallet): Wallet => ({
  id: raw.walletId,
  availableMinor: raw.availableBalanceMinor,
  reservedMinor: raw.reservedBalanceMinor,
  currency: raw.currency,
  updatedAt: new Date().toISOString(),
})
const toTransaction = (raw: RawTransaction): Transaction => ({
  id: raw.paymentId,
  paymentId: raw.paymentId,
  merchant: raw.merchantId,
  amountMinor: raw.amountMinor,
  currency: raw.currency,
  status: raw.status,
  createdAt: raw.createdAt,
})
const toPayment = (raw: RawPayment): Payment => ({
  ...toTransaction(raw),
  idempotencyKey: '',
  riskLevel: raw.riskScore == null ? undefined : raw.riskScore >= 0.85 ? 'CRITICAL' : raw.riskScore >= 0.45 ? 'HIGH' : 'LOW',
  timeline: [{ status: raw.status, at: raw.completedAt ?? raw.createdAt, detail: raw.fraudDecision }],
})
const liveWallet = async () => toWallet(await request<RawWallet>('/api/v1/wallets/me'))
const liveNotifications = async (): Promise<Notification[]> => (await request<RawNotification[]>('/api/v1/notifications/me')).map((raw) => {
  let detail = ''
  try { detail = JSON.parse(raw.payloadJson ?? '{}').paymentId ?? '' } catch { detail = raw.payloadJson ?? '' }
  return { id: raw.id, title: raw.template.replaceAll('_', ' '), message: detail, type: raw.template.includes('PAYMENT') ? 'PAYMENT' : 'SYSTEM', read: raw.status === 'SENT', createdAt: raw.createdAt }
})

export const api = {
  me: async () => {
    if (demoMode) return wait(demo.user)
    const raw = await request<RawUser>('/api/v1/users/me')
    const names = raw.fullName.trim().split(/\s+/)
    return { id: raw.id, email: raw.email, firstName: names[0] ?? '', lastName: names.slice(1).join(' '), roles: [raw.role.toLowerCase()], kycStatus: raw.kycStatus, createdAt: raw.createdAt }
  },
  dashboard: async () => {
    if (demoMode) return wait<DashboardSummary>({ wallet, recentTransactions: transactions.slice(0, 4), unreadNotifications: 2, spendThisMonthMinor: 70644 })
    const [walletResult, transactionPage, notices, payments] = await Promise.all([
      liveWallet(),
      request<RawPage<RawTransaction>>('/api/v1/transactions?page=0&size=4&sort=createdAt,desc'),
      liveNotifications(),
      request<RawPayment[]>('/api/v1/payments'),
    ])
    const month = new Date().getMonth()
    const year = new Date().getFullYear()
    const spendThisMonthMinor = payments.filter((item) => item.status === 'COMPLETED' && new Date(item.createdAt).getMonth() === month && new Date(item.createdAt).getFullYear() === year).reduce((sum, item) => sum + item.amountMinor, 0)
    return { wallet: walletResult, recentTransactions: transactionPage.content.map(toTransaction), unreadNotifications: notices.filter((item) => !item.read).length, spendThisMonthMinor }
  },
  wallet: () => demoMode ? wait(wallet) : liveWallet(),
  ledger: async () => {
    if (demoMode) return wait(demo.ledger)
    const [entries, walletResult] = await Promise.all([request<RawLedger[]>('/api/v1/wallets/me/ledger'), liveWallet()])
    return entries.map((raw) => ({ id: raw.id, type: raw.entryType, amountMinor: raw.amountMinor, currency: walletResult.currency, description: raw.entryType, balanceAfterMinor: raw.availableBalanceAfterMinor, createdAt: raw.createdAt }))
  },
  createPayment: async (body: PaymentRequest) => {
    if (demoMode) return wait<Payment>({ id: `pay_${crypto.randomUUID().slice(0, 8)}`, ...body, paymentId: '', status: 'AUTHORIZING', riskLevel: 'LOW', createdAt: iso(), timeline: [{ status: 'CREATED', at: iso() }, { status: 'AUTHORIZING', at: iso() }] })
    const raw = await request<RawPayment>('/api/v1/payments', { method: 'POST', headers: { 'Idempotency-Key': body.idempotencyKey }, body: JSON.stringify({ amountMinor: body.amountMinor, currency: body.currency, merchantId: body.merchant, country: body.country ?? 'US' }) })
    return toPayment(raw)
  },
  payment: async (id: string) => toPayment(await request<RawPayment>(`/api/v1/payments/${id}`)),
  transactions: (page: number, size: number, status?: string, query?: string) => {
    if (!demoMode) {
      const params = new URLSearchParams({ page: String(page), size: String(size), sort: 'createdAt,desc' })
      if (status) params.set('status', status)
      if (query) params.set('query', query)
      return request<RawPage<RawTransaction>>(`/api/v1/transactions?${params}`).then((raw) => ({ content: raw.content.map(toTransaction), page: raw.number, size: raw.size, totalElements: raw.totalElements, totalPages: raw.totalPages }))
    }
    const filtered = transactions.filter((item) => (!status || item.status === status) && (!query || item.merchant.toLowerCase().includes(query.toLowerCase())))
    return wait(pageOf(filtered, page, size))
  },
  risk: async (id: string) => {
    if (demoMode) return wait<RiskAnalysis>({ paymentId: id, score: id === 'pay_2' ? 82 : 18, level: id === 'pay_2' ? 'HIGH' : 'LOW', reasons: id === 'pay_2' ? ['New device fingerprint', 'Velocity threshold exceeded'] : ['Trusted device', 'Consistent behavior'], modelVersion: 'risk-v4.2', evaluatedAt: iso() })
    const raw = await request<RawRisk>(`/api/v1/fraud/transactions/${id}/risk-analysis`)
    const score = Math.round(raw.riskScore * 100)
    return { paymentId: raw.paymentId, score, level: score >= 85 ? 'CRITICAL' : score >= 45 ? 'HIGH' : 'LOW', reasons: raw.triggeredRules ? raw.triggeredRules.split(',') : [raw.explanation], modelVersion: 'rule-based-v1', evaluatedAt: new Date().toISOString() }
  },
  notifications: () => demoMode ? wait(notifications) : liveNotifications(),
  reviews: async () => {
    if (demoMode) return wait(demo.reviews)
    const rows = await request<RawPayment[]>('/api/v1/payments/admin/review-queue')
    return rows.map((raw) => ({ paymentId: raw.paymentId, transaction: toTransaction(raw), riskScore: Math.round((raw.riskScore ?? 0) * 100), reasons: [raw.fraudDecision ?? 'Manual review required'], state: 'PENDING' as const, queuedAt: raw.createdAt }))
  },
  decideReview: (paymentId: string, action: 'approve' | 'reject') => demoMode ? wait(undefined) : request<void>(`/api/v1/payments/${paymentId}/review/${action}`, { method: 'POST' }),
  mismatches: () => demoMode ? wait(demo.mismatches) : request<RawMismatch[]>('/api/v1/payments/admin/reconciliation/mismatches'),
  status: () => demoMode ? wait<SystemStatus>({ overall: 'OPERATIONAL', checkedAt: iso(), services: [
    { name: 'Payments', status: 'UP', latencyMs: 42, circuitState: 'CLOSED' },
    { name: 'Wallets', status: 'UP', latencyMs: 31, circuitState: 'CLOSED' },
    { name: 'Fraud intelligence', status: 'DEGRADED', latencyMs: 186, circuitState: 'HALF_OPEN' },
    { name: 'Notifications', status: 'UP', latencyMs: 18, circuitState: 'CLOSED' },
  ] }) : request<{ timestamp: string; services: Record<string, { status?: string }> }>('/api/v1/system/status').then((raw) => {
    const services = Object.entries(raw.services).map(([name, health]) => ({ name, status: health.status === 'UP' ? 'UP' as const : 'DOWN' as const, circuitState: 'CLOSED' as const }))
    return { overall: services.every((service) => service.status === 'UP') ? 'OPERATIONAL' as const : 'DEGRADED' as const, checkedAt: raw.timestamp, services }
  }),
}
