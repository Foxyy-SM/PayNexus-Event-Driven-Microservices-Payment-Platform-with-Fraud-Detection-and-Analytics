import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate } from 'react-router-dom'
import {
  Activity, ArrowDownLeft, ArrowRight, ArrowUpRight, Bell, Check, CheckCircle2, CircleAlert,
  Clock3, Copy, CreditCard, Fingerprint, Gauge, Landmark, LockKeyhole, Search, ShieldCheck,
  ShieldQuestion, Sparkles, UserCheck, WalletCards, X, XCircle, Zap,
} from 'lucide-react'
import { api } from './api'
import { useAuth } from './auth'
import { EmptyState, ErrorState, LoadingState, RowLink } from './App'
import type { Currency, Payment, Transaction } from './types'

const formatMoney = (minor: number, currency: string) => new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(minor / 100)
const formatDate = (value: string) => new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(value))
const tone = (value: string) => value.toLowerCase().replace('_', '-')

function StatusBadge({ status }: { status: string }) {
  return <span className={`badge ${tone(status)}`}><span />{status.replaceAll('_', ' ')}</span>
}

function Toast({ children, onClose }: { children: React.ReactNode; onClose: () => void }) {
  return <div className="toast" role="status"><CheckCircle2 />{children}<button onClick={onClose} aria-label="Close notification"><X /></button></div>
}

function TransactionRows({ items, onSelect }: { items: Transaction[]; onSelect?: (item: Transaction) => void }) {
  return <div className="transaction-list">{items.map((item) => (
    <button className="transaction-row" key={item.id} onClick={() => onSelect?.(item)}>
      <span className={`merchant-icon ${tone(item.status)}`}>{item.status === 'FAILED' ? <XCircle /> : <ArrowUpRight />}</span>
      <span className="row-main"><strong>{item.merchant}</strong><small>{formatDate(item.createdAt)} · {item.id}</small></span>
      <span className="row-value"><strong>-{formatMoney(item.amountMinor, item.currency)}</strong><StatusBadge status={item.status} /></span>
      {onSelect && <RowLink />}
    </button>
  ))}</div>
}

export function Landing() {
  const auth = useAuth()
  if (auth.authenticated) return <Navigate to="/dashboard" replace />
  return <div className="landing">
    <header className="landing-nav"><div className="brand"><span className="brand-mark"><CreditCard size={19} /></span><span>paynexus</span></div><span className="secure-pill"><LockKeyhole />Bank-grade protection</span></header>
    <main className="hero">
      <div className="hero-copy"><span className="eyebrow"><Sparkles />Money, made lucid</span><h1>Move with confidence.<br /><em>Live without limits.</em></h1><p>A private space for your money. Pay, track, and protect every transaction with intelligence built in.</p><button className="button hero-button" onClick={auth.login}>Enter your account <ArrowRight /></button><div className="trust-row"><span><ShieldCheck />PKCE secured</span><span><Fingerprint />Identity protected</span><span><Zap />Real-time controls</span></div></div>
      <div className="hero-visual" aria-hidden="true">
        <div className="orb orb-one" /><div className="orb orb-two" />
        <div className="floating-card primary-card"><div className="card-top"><span>paynexus</span><span className="card-chip" /></div><p>Available balance</p><strong>$12,684.00</strong><div className="card-bottom"><span>•••• 2486</span><span>VIRTUAL</span></div></div>
        <div className="floating-card insight-card"><span className="success-icon"><Check /></span><div><small>Payment protected</small><strong>Risk check passed</strong></div></div>
        <div className="floating-card score-card"><Gauge /><div><small>Security score</small><strong>Exceptional · 96</strong></div></div>
      </div>
    </main>
    <footer className="landing-foot">© 2026 PayNexus Systems <span>Privacy · Security · Terms</span></footer>
  </div>
}

export function Dashboard() {
  const query = useQuery({ queryKey: ['dashboard'], queryFn: api.dashboard })
  if (query.isLoading) return <LoadingState />
  if (query.isError || !query.data) return <ErrorState retry={() => void query.refetch()} />
  const { wallet, recentTransactions, unreadNotifications, spendThisMonthMinor } = query.data
  return <div className="page-stack">
    <section className="welcome"><div><h2>Good morning, Avery.</h2><p>Your finances are in great shape today.</p></div><Link className="button" to="/pay"><ArrowUpRight />Make a payment</Link></section>
    <section className="metrics-grid">
      <div className="balance-card"><div className="balance-label"><WalletCards />Available balance</div><strong>{formatMoney(wallet.availableMinor, wallet.currency)}</strong><p>{formatMoney(wallet.reservedMinor, wallet.currency)} reserved</p><div className="balance-actions"><Link to="/pay">Send money <ArrowRight /></Link><Link to="/wallet">View wallet</Link></div></div>
      <div className="metric-card"><span className="metric-icon"><Activity /></span><p>Spent this month</p><strong>{formatMoney(spendThisMonthMinor, wallet.currency)}</strong><small className="positive">↓ 12.4% from last month</small></div>
      <div className="metric-card"><span className="metric-icon violet"><Bell /></span><p>Needs attention</p><strong>{unreadNotifications}</strong><small>Unread notifications</small></div>
    </section>
    <section className="content-grid"><div className="panel wide"><div className="panel-head"><div><span className="eyebrow">Latest activity</span><h2>Recent transactions</h2></div><Link to="/transactions">View all <ArrowRight /></Link></div><TransactionRows items={recentTransactions} /></div>
      <div className="panel"><div className="panel-head"><div><span className="eyebrow">Protection</span><h2>Security pulse</h2></div></div><div className="security-score"><div className="score-ring"><strong>96</strong><span>/100</span></div><h3>Exceptional</h3><p>All account safeguards are active.</p></div><ul className="check-list"><li><Check />Identity verified</li><li><Check />Device recognized</li><li><Check />No unusual activity</li></ul></div></section>
  </div>
}

export function Pay() {
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState<Currency>('USD')
  const [merchant, setMerchant] = useState('')
  const [idempotencyKey, setKey] = useState(() => crypto.randomUUID())
  const mutation = useMutation({ mutationFn: api.createPayment })
  const submit = (event: FormEvent) => {
    event.preventDefault()
    const minor = Math.round(Number(amount) * 100)
    if (!minor || !merchant.trim()) return
    mutation.mutate({ amountMinor: minor, currency, merchant: merchant.trim(), idempotencyKey })
  }
  if (mutation.data) return <PaymentResult payment={mutation.data} onReset={() => { mutation.reset(); setAmount(''); setMerchant(''); setKey(crypto.randomUUID()) }} />
  return <div className="split-layout"><section><span className="eyebrow">Secure transfer</span><h2 className="page-title">Create a payment</h2><p className="page-subtitle">Every payment is screened in real time before money moves.</p>
    <form className="panel payment-form" onSubmit={submit}>
      <label>Amount<div className="amount-input"><select value={currency} onChange={(e) => setCurrency(e.target.value as Currency)} aria-label="Currency">{(['USD', 'EUR', 'GBP', 'INR'] as Currency[]).map((item) => <option key={item}>{item}</option>)}</select><input required min="0.01" step="0.01" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0.00" aria-label="Payment amount" /></div><small>Sent as {Math.round(Number(amount || 0) * 100)} minor units</small></label>
      <label>Merchant<input required value={merchant} onChange={(e) => setMerchant(e.target.value)} placeholder="Who are you paying?" /></label>
      <label>Idempotency key<div className="input-action"><input readOnly value={idempotencyKey} /><button type="button" onClick={() => void navigator.clipboard.writeText(idempotencyKey)} aria-label="Copy idempotency key"><Copy /></button></div><small>Prevents accidental duplicate charges.</small></label>
      {mutation.isError && <div className="inline-error"><CircleAlert />{mutation.error.message}</div>}
      <button className="button full" disabled={mutation.isPending}>{mutation.isPending ? <><span className="spinner" />Authorizing…</> : <>Review & authorize <ArrowRight /></>}</button>
    </form></section>
    <aside className="panel assurance"><span className="assurance-icon"><ShieldCheck /></span><h3>Protected by design</h3><p>Your payment is encrypted and evaluated by adaptive fraud controls.</p><ul><li><Check />No sensitive data stored locally</li><li><Check />Duplicate payment protection</li><li><Check />Real-time status updates</li></ul></aside></div>
}

function PaymentResult({ payment, onReset }: { payment: Payment; onReset: () => void }) {
  return <div className="result-wrap"><div className="panel result-card"><span className="result-icon"><Check /></span><span className="eyebrow">Payment initiated</span><h2>{formatMoney(payment.amountMinor, payment.currency)}</h2><p>to {payment.merchant}</p><StatusBadge status={payment.status} /><div className="timeline">{payment.timeline.map((item, index) => <div className="timeline-item" key={`${item.status}-${index}`}><span><Check /></span><div><strong>{item.status.replace('_', ' ')}</strong><small>{formatDate(item.at)}</small></div></div>)}</div><div className="reference"><span>Payment reference</span><code>{payment.id}</code></div><button className="button full" onClick={onReset}>Make another payment</button></div></div>
}

export function Wallet() {
  const wallet = useQuery({ queryKey: ['wallet'], queryFn: api.wallet })
  const ledger = useQuery({ queryKey: ['ledger'], queryFn: api.ledger })
  if (wallet.isLoading || ledger.isLoading) return <LoadingState />
  if (wallet.isError || ledger.isError || !wallet.data || !ledger.data) return <ErrorState />
  return <div className="page-stack"><div className="wallet-hero"><div><span className="eyebrow">Primary wallet</span><p>Total funds</p><strong>{formatMoney(wallet.data.availableMinor + wallet.data.reservedMinor, wallet.data.currency)}</strong></div><div className="wallet-breakdown"><span><small>Available</small><strong>{formatMoney(wallet.data.availableMinor, wallet.data.currency)}</strong></span><span><small>Reserved</small><strong>{formatMoney(wallet.data.reservedMinor, wallet.data.currency)}</strong></span></div></div>
    <section className="panel"><div className="panel-head"><div><span className="eyebrow">Movement</span><h2>Wallet ledger</h2></div></div>{ledger.data.length ? <div className="transaction-list">{ledger.data.map((entry) => <div className="transaction-row static" key={entry.id}><span className={`merchant-icon ${entry.type === 'CREDIT' || entry.type === 'RELEASE' ? 'captured' : ''}`}>{entry.type === 'CREDIT' ? <ArrowDownLeft /> : <ArrowUpRight />}</span><span className="row-main"><strong>{entry.description}</strong><small>{formatDate(entry.createdAt)} · {entry.type}</small></span><span className="row-value"><strong>{entry.type === 'CREDIT' ? '+' : '-'}{formatMoney(entry.amountMinor, entry.currency)}</strong><small>Balance {formatMoney(entry.balanceAfterMinor, entry.currency)}</small></span></div>)}</div> : <EmptyState title="No ledger entries" detail="Wallet movements will appear here." />}</section></div>
}

export function Transactions() {
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Transaction | null>(null)
  const query = useQuery({ queryKey: ['transactions', page, status, search], queryFn: () => api.transactions(page, 4, status, search) })
  const risk = useQuery({ queryKey: ['risk', selected?.paymentId], queryFn: () => api.risk(selected!.paymentId), enabled: Boolean(selected) })
  return <div className="page-stack"><div><span className="eyebrow">Complete history</span><h2 className="page-title">Every move, accounted for.</h2></div><section className="panel">
    <div className="filters"><div className="search-field"><Search /><input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0) }} placeholder="Search merchant" aria-label="Search transactions" /></div><select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} aria-label="Filter by status"><option value="">All statuses</option>{['COMPLETED', 'PENDING_REVIEW', 'CAPTURING', 'REJECTED', 'FAILED'].map((item) => <option key={item} value={item}>{item.replaceAll('_', ' ')}</option>)}</select></div>
    {query.isLoading ? <LoadingState /> : query.isError ? <ErrorState retry={() => void query.refetch()} /> : query.data?.content.length ? <><TransactionRows items={query.data.content} onSelect={setSelected} /><div className="pagination"><span>{query.data.totalElements} transactions</span><div><button disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button><span>{page + 1} / {Math.max(query.data.totalPages, 1)}</span><button disabled={page + 1 >= query.data.totalPages} onClick={() => setPage((value) => value + 1)}>Next</button></div></div></> : <EmptyState title="No matching transactions" detail="Try changing your search or status filter." />}
  </section>{selected && <><button className="drawer-backdrop" onClick={() => setSelected(null)} aria-label="Close risk analysis" /><aside className="drawer" aria-label="Risk analysis drawer"><div className="drawer-head"><div><span className="eyebrow">Transaction intelligence</span><h2>Risk analysis</h2></div><button className="icon-button" onClick={() => setSelected(null)}><X /></button></div><div className="drawer-merchant"><span className="merchant-icon"><ArrowUpRight /></span><div><strong>{selected.merchant}</strong><small>{formatMoney(selected.amountMinor, selected.currency)} · {selected.paymentId}</small></div></div>{risk.isLoading ? <LoadingState /> : risk.data && <><div className={`risk-score ${tone(risk.data.level)}`}><strong>{risk.data.score}</strong><span>Risk score<br /><b>{risk.data.level}</b></span></div><h3>Signals observed</h3><ul className="signal-list">{risk.data.reasons.map((reason) => <li key={reason}><ShieldQuestion />{reason}</li>)}</ul><div className="meta-grid"><span>Model<strong>{risk.data.modelVersion}</strong></span><span>Evaluated<strong>{formatDate(risk.data.evaluatedAt)}</strong></span></div></>}</aside></>}</div>
}

export function Notifications() {
  const query = useQuery({ queryKey: ['notifications'], queryFn: api.notifications })
  if (query.isLoading) return <LoadingState />
  if (query.isError) return <ErrorState retry={() => void query.refetch()} />
  return <section className="panel"><div className="panel-head"><div><span className="eyebrow">Inbox</span><h2>Notifications</h2></div><button className="text-button">Mark all as read</button></div>{query.data?.length ? <div className="notification-list">{query.data.map((item) => <article className={`notification ${item.read ? '' : 'unread'}`} key={item.id}><span className="notification-icon">{item.type === 'SECURITY' ? <ShieldCheck /> : item.type === 'PAYMENT' ? <CreditCard /> : <Activity />}</span><div><div><strong>{item.title}</strong><time>{formatDate(item.createdAt)}</time></div><p>{item.message}</p></div></article>)}</div> : <EmptyState icon={Bell} title="You’re all caught up" detail="Important account updates will appear here." />}</section>
}

export function Profile() {
  const query = useQuery({ queryKey: ['me'], queryFn: api.me })
  if (query.isLoading) return <LoadingState />
  if (query.isError || !query.data) return <ErrorState />
  const user = query.data
  return <div className="profile-grid"><section className="panel profile-card"><div className="profile-avatar">{user.firstName[0]}{user.lastName[0]}<span><Check /></span></div><h2>{user.firstName} {user.lastName}</h2><p>{user.email}</p><StatusBadge status="VERIFIED" /><div className="profile-meta"><span>Member since<strong>{new Date(user.createdAt).getFullYear()}</strong></span><span>Account ID<strong>{user.id}</strong></span></div></section><section className="panel kyc-card"><span className="kyc-icon"><UserCheck /></span><div><span className="eyebrow">Identity</span><h2>Verification complete</h2><p>Your identity has been verified. You have full access to PayNexus payment and wallet features.</p></div><div className="kyc-steps"><span className="done"><Check />Personal details</span><span className="done"><Check />Identity document</span><span className="done"><Check />Security screening</span></div></section></div>
}

export function AdminReviews() {
  const client = useQueryClient()
  const [toast, setToast] = useState('')
  const query = useQuery({ queryKey: ['reviews'], queryFn: api.reviews })
  const mutation = useMutation({ mutationFn: ({ id, action }: { id: string; action: 'approve' | 'reject' }) => api.decideReview(id, action), onSuccess: (_, variables) => { setToast(`Payment ${variables.action}d`); void client.invalidateQueries({ queryKey: ['reviews'] }) } })
  if (query.isLoading) return <LoadingState />
  if (query.isError) return <ErrorState />
  return <div className="page-stack"><div className="ops-banner"><ShieldQuestion /><div><span className="eyebrow">Decision desk</span><h2>Human judgment, when it matters.</h2><p>Review high-risk payments flagged by the intelligence engine.</p></div></div><section className="panel"><div className="panel-head"><div><h2>Review queue</h2><span className="badge risk-review"><span />{query.data?.length ?? 0} pending</span></div></div>{query.data?.length ? query.data.map((review) => <article className="review-card" key={review.paymentId}><div className="review-score"><strong>{review.riskScore}</strong><small>HIGH RISK</small></div><div className="review-main"><h3>{review.transaction.merchant}<StatusBadge status={review.transaction.status} /></h3><p>{formatMoney(review.transaction.amountMinor, review.transaction.currency)} · {review.paymentId} · {formatDate(review.queuedAt)}</p><ul>{review.reasons.map((reason) => <li key={reason}><CircleAlert />{reason}</li>)}</ul></div><div className="review-actions"><button className="button secondary danger" disabled={mutation.isPending} onClick={() => mutation.mutate({ id: review.paymentId, action: 'reject' })}><X />Reject</button><button className="button" disabled={mutation.isPending} onClick={() => mutation.mutate({ id: review.paymentId, action: 'approve' })}><Check />Approve</button></div></article>) : <EmptyState title="Queue cleared" detail="There are no payments waiting for manual review." />}</section>{toast && <Toast onClose={() => setToast('')}>{toast}</Toast>}</div>
}

export function Reconciliation() {
  const query = useQuery({ queryKey: ['mismatches'], queryFn: api.mismatches })
  if (query.isLoading) return <LoadingState />
  if (query.isError) return <ErrorState />
  return <div className="page-stack"><div className="ops-banner teal"><Landmark /><div><span className="eyebrow">Settlement integrity</span><h2>Reconciliation incidents</h2><p>Exceptions between payment state and wallet-ledger evidence.</p></div></div><section className="panel"><div className="panel-head"><div><h2>Detected mismatches</h2></div></div>{query.data?.length ? <div className="table-wrap"><table><thead><tr><th>Incident</th><th>Payment</th><th>Type</th><th>Expected evidence</th><th>Actual evidence</th><th>Repair</th><th>Status</th></tr></thead><tbody>{query.data.map((item) => <tr key={item.id}><td><strong>{item.id}</strong><small>{formatDate(item.detectedAt)}</small></td><td>{item.paymentId}</td><td>{item.mismatchType.replaceAll('_', ' ')}</td><td>{item.expectedEvidence}</td><td>{item.actualEvidence}</td><td>{item.repairAction}</td><td><StatusBadge status={item.repairStatus} /></td></tr>)}</tbody></table></div> : <EmptyState title="Books are balanced" detail="No reconciliation mismatches were detected." />}</section></div>
}

export function Status() {
  const query = useQuery({ queryKey: ['status'], queryFn: api.status, refetchInterval: 30_000 })
  if (query.isLoading) return <LoadingState />
  if (query.isError || !query.data) return <ErrorState />
  return <div className="page-stack"><div className="status-hero"><span className="status-pulse" /><div><span className="eyebrow">Live infrastructure</span><h2>All core systems operational</h2><p>Last checked {formatDate(query.data.checkedAt)} · refreshes every 30 seconds</p></div></div><section className="panel"><div className="panel-head"><div><h2>Service health</h2></div><StatusBadge status={query.data.overall} /></div><div className="service-list">{query.data.services.map((service) => <div className="service-row" key={service.name}><span className={`service-dot ${tone(service.status)}`} /><strong>{service.name}</strong><span className="latency">{service.latencyMs} ms</span><span className={`circuit ${tone(service.circuitState)}`}><Activity />Circuit {service.circuitState.replace('_', ' ')}</span><StatusBadge status={service.status} /></div>)}</div></section><div className="status-note"><Clock3 /><div><strong>Continuous monitoring</strong><p>Health and circuit-breaker states are reported directly by each service.</p></div></div></div>
}
