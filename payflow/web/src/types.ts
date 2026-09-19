export type Currency = 'USD' | 'EUR' | 'GBP' | 'INR'
export type PaymentStatus = 'CREATED' | 'RESERVED' | 'AUTHORIZING' | 'RISK_REVIEW' | 'PENDING_REVIEW' | 'AUTHORIZED' | 'CAPTURING' | 'CAPTURED' | 'COMPLETED' | 'FAILED' | 'REJECTED'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  roles: string[]
  kycStatus: 'NOT_STARTED' | 'PENDING' | 'VERIFIED' | 'REJECTED'
  createdAt: string
}

export interface Money { amountMinor: number; currency: Currency }
export interface Wallet {
  id: string
  availableMinor: number
  reservedMinor: number
  currency: Currency
  updatedAt: string
}

export interface LedgerEntry {
  id: string
  type: 'CREDIT' | 'DEBIT' | 'RESERVE' | 'CAPTURE' | 'RELEASE'
  amountMinor: number
  currency: Currency
  description: string
  balanceAfterMinor: number
  createdAt: string
}

export interface Transaction {
  id: string
  paymentId: string
  merchant: string
  amountMinor: number
  currency: Currency
  status: PaymentStatus
  riskLevel?: RiskLevel
  createdAt: string
}

export interface Payment extends Transaction {
  idempotencyKey: string
  timeline: Array<{ status: PaymentStatus; at: string; detail?: string }>
}

export interface DashboardSummary {
  wallet: Wallet
  recentTransactions: Transaction[]
  unreadNotifications: number
  spendThisMonthMinor: number
}

export interface Notification {
  id: string
  title: string
  message: string
  type: 'PAYMENT' | 'SECURITY' | 'SYSTEM'
  read: boolean
  createdAt: string
}

export interface RiskAnalysis {
  paymentId: string
  score: number
  level: RiskLevel
  reasons: string[]
  modelVersion: string
  evaluatedAt: string
}

export interface FraudReview {
  paymentId: string
  transaction: Transaction
  riskScore: number
  reasons: string[]
  state: 'PENDING' | 'APPROVED' | 'REJECTED'
  queuedAt: string
}

export interface ReconciliationMismatch {
  id: string
  paymentId: string
  mismatchType: string
  expectedEvidence: string
  actualEvidence: string
  repairAction: string
  repairStatus: string
  detectedAt: string
}

export interface ServiceStatus {
  name: string
  status: 'UP' | 'DEGRADED' | 'DOWN'
  latencyMs?: number
  circuitState: 'CLOSED' | 'HALF_OPEN' | 'OPEN'
}

export interface SystemStatus {
  overall: 'OPERATIONAL' | 'DEGRADED' | 'OUTAGE'
  services: ServiceStatus[]
  checkedAt: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface PaymentRequest {
  amountMinor: number
  currency: Currency
  merchant: string
  idempotencyKey: string
  country?: string
}
