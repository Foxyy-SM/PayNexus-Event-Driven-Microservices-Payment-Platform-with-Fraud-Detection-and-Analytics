import Keycloak, { type KeycloakConfig } from 'keycloak-js'
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

const config: KeycloakConfig = {
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8088',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'paynexus',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'paynexus-web',
}

const keycloak = new Keycloak(config)
const demoMode = (import.meta.env.VITE_DEMO_MODE ?? 'false') === 'true'

interface AuthState {
  ready: boolean
  authenticated: boolean
  token?: string
  username: string
  roles: string[]
  login: () => void
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(demoMode)
  const [authenticated, setAuthenticated] = useState(demoMode)
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    if (demoMode) return
    keycloak
      .init({ onLoad: 'check-sso', pkceMethod: 'S256', checkLoginIframe: false })
      .then((value) => setAuthenticated(value))
      .catch(() => setAuthenticated(false))
      .finally(() => setReady(true))
  }, [])

  useEffect(() => {
    if (!authenticated || demoMode) return
    const timer = window.setInterval(() => void keycloak.updateToken(60), 30_000)
    return () => window.clearInterval(timer)
  }, [authenticated])

  const value = useMemo<AuthState>(() => ({
    ready,
    authenticated,
    token: keycloak.token,
    username: demoMode ? 'Avery Stone' : (keycloak.tokenParsed?.preferred_username as string | undefined) ?? 'Member',
    roles: (demoMode ? ['user', 'admin'] : keycloak.realmAccess?.roles ?? []).map((role) => role.toLowerCase()),
    login: () => {
      if (demoMode) {
        setAuthenticated(true)
        setRevision((value) => value + 1)
      } else void keycloak.login({ redirectUri: `${window.location.origin}/dashboard` })
    },
    logout: () => {
      if (demoMode) setAuthenticated(false)
      else void keycloak.logout({ redirectUri: window.location.origin })
    },
  // revision ensures demo auth actions update context
  }), [ready, authenticated, revision])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used within AuthProvider')
  return value
}

export function RequireAuth({ children, roles }: { children: ReactNode; roles?: string[] }) {
  const auth = useAuth()
  const location = useLocation()
  if (!auth.ready) return <div className="fullscreen-state"><span className="spinner" />Securing your session…</div>
  if (!auth.authenticated) return <Navigate to="/" replace state={{ from: location }} />
  if (roles && !roles.some((role) => auth.roles.includes(role.toLowerCase()))) return <Navigate to="/dashboard" replace />
  return children
}
