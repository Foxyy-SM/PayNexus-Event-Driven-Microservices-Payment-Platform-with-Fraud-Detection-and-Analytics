import { useState, type ReactNode } from 'react'
import { NavLink, Route, Routes, useLocation } from 'react-router-dom'
import {
  Activity, Bell, BookOpen, ChevronRight, CircleDollarSign, CreditCard, Gauge, Landmark,
  LayoutDashboard, LogOut, Menu, ScanSearch, Send, ShieldCheck, UserRound, WalletCards, X,
} from 'lucide-react'
import { useAuth, RequireAuth } from './auth'
import {
  AdminReviews, Dashboard, Landing, Notifications, Pay, Profile, Reconciliation,
  Status, Transactions, Wallet,
} from './pages'

const mainNav = [
  { to: '/dashboard', label: 'Overview', icon: LayoutDashboard },
  { to: '/pay', label: 'Send payment', icon: Send },
  { to: '/wallet', label: 'Wallet', icon: WalletCards },
  { to: '/transactions', label: 'Transactions', icon: BookOpen },
  { to: '/notifications', label: 'Notifications', icon: Bell },
  { to: '/profile', label: 'Profile & KYC', icon: UserRound },
]
const opsNav = [
  { to: '/admin/fraud', label: 'Fraud reviews', icon: ScanSearch },
  { to: '/admin/reconciliation', label: 'Reconciliation', icon: Landmark },
  { to: '/status', label: 'System status', icon: Gauge },
]

function Brand() {
  return <div className="brand"><span className="brand-mark"><CreditCard size={19} /></span><span>paynexus</span></div>
}

function Shell({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const auth = useAuth()
  const location = useLocation()
  const title = [...mainNav, ...opsNav].find((item) => item.to === location.pathname)?.label ?? 'PayNexus'
  const nav = (items: typeof mainNav) => items.map(({ to, label, icon: Icon }) => (
    <NavLink key={to} to={to} onClick={() => setOpen(false)} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
      <Icon size={18} /><span>{label}</span>{to === '/notifications' && <span className="nav-count">2</span>}
    </NavLink>
  ))
  return (
    <div className="app-shell">
      <button className="mobile-menu" onClick={() => setOpen(true)} aria-label="Open navigation"><Menu /></button>
      {open && <button className="nav-backdrop" onClick={() => setOpen(false)} aria-label="Close navigation" />}
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="sidebar-head"><Brand /><button className="icon-button mobile-close" onClick={() => setOpen(false)} aria-label="Close navigation"><X /></button></div>
        <nav aria-label="Primary navigation">{nav(mainNav)}<p className="nav-heading">Operations</p>{nav(opsNav)}</nav>
        <div className="sidebar-foot">
          <div className="user-avatar">{auth.username.split(' ').map((word) => word[0]).join('').slice(0, 2)}</div>
          <div><strong>{auth.username}</strong><span>Verified member</span></div>
          <button className="icon-button" onClick={auth.logout} aria-label="Sign out"><LogOut size={17} /></button>
        </div>
      </aside>
      <main className="main">
        <header className="topbar"><div><span className="eyebrow">Personal console</span><h1>{title}</h1></div><div className="secure-pill"><ShieldCheck size={15} />Protected session</div></header>
        <div className="page-container">{children}</div>
      </main>
    </div>
  )
}

function Protected({ children, admin = false }: { children: ReactNode; admin?: boolean }) {
  return <RequireAuth roles={admin ? ['admin'] : undefined}><Shell>{children}</Shell></RequireAuth>
}

export function EmptyState({ icon = Activity, title, detail }: { icon?: typeof Activity; title: string; detail: string }) {
  const Icon = icon
  return <div className="empty-state"><Icon /><h3>{title}</h3><p>{detail}</p></div>
}

export function LoadingState() {
  return <div className="loading-state" aria-live="polite"><span className="spinner" />Loading secure data…</div>
}

export function ErrorState({ retry }: { retry?: () => void }) {
  return <div className="error-state"><CircleDollarSign /><div><strong>We couldn’t load this view</strong><p>Check your connection and try again.</p></div>{retry && <button className="button secondary" onClick={retry}>Try again</button>}</div>
}

export function RowLink() { return <ChevronRight size={17} className="muted" /> }

export function App() {
  return <Routes>
    <Route path="/" element={<Landing />} />
    <Route path="/dashboard" element={<Protected><Dashboard /></Protected>} />
    <Route path="/pay" element={<Protected><Pay /></Protected>} />
    <Route path="/wallet" element={<Protected><Wallet /></Protected>} />
    <Route path="/transactions" element={<Protected><Transactions /></Protected>} />
    <Route path="/notifications" element={<Protected><Notifications /></Protected>} />
    <Route path="/profile" element={<Protected><Profile /></Protected>} />
    <Route path="/admin/fraud" element={<Protected admin><AdminReviews /></Protected>} />
    <Route path="/admin/reconciliation" element={<Protected admin><Reconciliation /></Protected>} />
    <Route path="/status" element={<Protected><Status /></Protected>} />
  </Routes>
}
