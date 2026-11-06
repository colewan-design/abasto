/**
 * Shapes returned by the Laravel API. Amounts always arrive as integer minor
 * units in a `*_minor` field, with a pre-formatted string alongside for
 * display — the client never does currency maths of its own.
 */

export type TransactionType = 'income' | 'expense' | 'transfer'
export type Direction = 'inflow' | 'outflow'
export type CategoryType = 'income' | 'expense'
export type AccountType = 'cash' | 'bank' | 'ewallet' | 'credit_card' | 'savings'
export type ReceiptStatus = 'pending' | 'processing' | 'processed' | 'failed' | 'confirmed'
export type BudgetStatus = 'ok' | 'approaching' | 'exceeded' | 'unset'

export interface Paginated<T> {
  data: T[]
  links?: { first: string; last: string; prev: string | null; next: string | null }
  meta?: Record<string, any>
}

export interface Envelope<T> {
  data: T
  meta?: Record<string, any>
}

export interface User {
  id: number
  name: string
  email: string
  avatar_url: string | null
  currency: { code: string; symbol: string; exponent: number }
  locale: string
  timezone: string
  is_guest: boolean
  has_password: boolean
  is_google_linked: boolean
  onboarding_completed: boolean
  app_lock_enabled: boolean
  created_at: string | null
}

export interface AuthResponse {
  token: string
  token_type: string
  expires_at: string | null
  user: User
  needs_onboarding: boolean
}

export interface Account {
  id: number
  name: string
  type: AccountType
  type_label: string
  is_liability: boolean
  currency_code: string
  opening_balance_minor: number
  current_balance_minor: number
  credit_limit_minor: number | null
  available_credit_minor: number | null
  institution: string | null
  color: string | null
  icon: string | null
  include_in_total: boolean
  is_archived: boolean
  sort_order: number
  transactions_count?: number
  formatted: { balance: string; opening_balance: string; available_credit: string | null }
}

export interface AccountSummary {
  id: number
  name: string
  type: AccountType
  type_label: string
  is_liability: boolean
  currency_code: string
  color: string | null
  icon: string | null
}

export interface CategoryGroupRef {
  id: number
  name: string
  color: string | null
  icon: string | null
}

export interface Category {
  id: number
  name: string
  type: CategoryType
  color: string | null
  icon: string | null
  parent_id: number | null
  category_group_id: number | null
  group?: CategoryGroupRef
  is_system: boolean
  is_archived: boolean
  keywords: string[]
  sort_order: number
  transactions_count?: number
}

export interface CategoryGroup {
  id: number
  name: string
  type: CategoryType
  color: string | null
  icon: string | null
  is_system: boolean
  sort_order: number
  categories?: Category[]
  categories_count?: number
}

export interface Tag {
  id: number
  name: string
  color: string | null
  transactions_count?: number
}

export interface TransactionSplit {
  id: number
  category_id: number | null
  category?: Category
  amount_minor: number
  formatted: string
  note: string | null
  sort_order: number
}

export interface Transaction {
  id: number
  type: TransactionType
  direction: Direction
  is_transfer: boolean
  is_savings_allocation: boolean
  amount_minor: number
  signed_amount_minor: number
  currency_code: string
  formatted_amount: string
  occurred_on: string
  occurred_at: string | null
  merchant: string | null
  note: string | null
  account_id: number
  account?: AccountSummary
  transfer_account_id: number | null
  transfer_account?: AccountSummary
  transfer_group: string | null
  category_id: number | null
  category?: Category
  tags?: Tag[]
  has_splits: boolean
  splits?: TransactionSplit[]
  receipt_id: number | null
  bill_id: number | null
  savings_goal_id: number | null
  recurring_rule_id: number | null
  created_via: string
  created_at: string | null
}

export interface ReceiptItem {
  id: number
  line_no: number
  description: string
  quantity: number
  unit_price_minor: number | null
  total_minor: number
  formatted: string
  category_id: number | null
  suggested_category_id: number | null
  effective_category_id: number | null
  category?: Category
  suggested_category?: Category
  confidence: number | null
  needs_review: boolean
}

export interface Receipt {
  id: number
  status: ReceiptStatus
  is_reviewable: boolean
  merchant: string | null
  receipt_date: string | null
  currency_code: string
  subtotal_minor: number | null
  tax_minor: number | null
  tip_minor: number | null
  discount_minor: number | null
  total_minor: number | null
  payment_method: string | null
  formatted: { subtotal: string | null; tax: string | null; total: string | null }
  items?: ReceiptItem[]
  items_count?: number
  extraction: {
    driver: string | null
    confidence: number | null
    field_confidence: Record<string, number>
    needs_review: string[]
    failure_reason: string | null
  }
  duplicate: {
    is_duplicate: boolean
    score: number | null
    reason: string | null
    receipt_id: number | null
    transaction_id: number | null
  }
  transaction_id?: number | null
  image_url: string
  thumbnail_url: string | null
  processed_at: string | null
  confirmed_at: string | null
  created_at: string | null
}

export interface BudgetLine {
  id: number
  category_id: number
  category: Category | null
  limit_minor: number
  amount_minor: number
  rollover_minor: number
  spent_minor: number
  remaining_minor: number
  percent_used: number | null
  status: BudgetStatus
  threshold: number
  formatted: { limit: string; spent: string; remaining: string }
}

export interface Budget {
  id: number
  period: string
  period_start: string
  period_end: string
  label: string
  currency_code: string
  overall_amount_minor: number | null
  rollover_enabled: boolean
  alert_threshold: number
  is_current: boolean
  categories?: Array<{
    id: number
    category_id: number
    category: Category
    amount_minor: number
    rollover_minor: number
    limit_minor: number
    alert_threshold: number
  }>
}

export interface BudgetView {
  data: Budget | null
  meta: {
    month: string
    label: string
    overall?: {
      limit_minor: number | null
      spent_minor: number
      remaining_minor: number | null
      percent_used: number | null
      status: BudgetStatus
      formatted: { limit: string | null; spent: string; remaining: string | null }
    }
    categories?: BudgetLine[]
    unbudgeted_minor?: number
    uncategorized_minor?: number
    suggestions?: Array<{ category_id: number; category: string; suggested_minor: number; formatted: string }>
  }
}

export interface Bill {
  id: number
  name: string
  payee: string | null
  amount_minor: number
  currency_code: string
  formatted_amount: string
  due_date: string
  days_until_due: number
  is_overdue: boolean
  next_due_date: string | null
  recurrence: string
  recurrence_label: string
  recurrence_interval: number
  reminder_days_before: number
  auto_pay: boolean
  is_active: boolean
  note: string | null
  last_paid_on: string | null
  account_id: number | null
  account?: Account
  category_id: number | null
  category?: Category
  payments?: BillPayment[]
}

export interface BillPayment {
  id: number
  bill_id: number
  period_due_date: string
  amount_minor: number
  formatted_amount: string
  paid_on: string | null
  status: 'unpaid' | 'paid' | 'skipped'
  transaction_id: number | null
}

export interface SavingsGoal {
  id: number
  name: string
  currency_code: string
  target_amount_minor: number
  current_amount_minor: number
  remaining_minor: number
  progress: number
  is_achieved: boolean
  achieved_at: string | null
  target_date: string | null
  suggested_monthly_minor: number | null
  formatted: { target: string; saved: string; remaining: string; suggested_monthly: string | null }
  color: string | null
  icon: string | null
  note: string | null
  is_archived: boolean
  sort_order: number
  account_id: number | null
  account?: Account
  contributions?: SavingsContribution[]
  contributions_count?: number
}

export interface SavingsContribution {
  id: number
  savings_goal_id: number
  amount_minor: number
  formatted_amount: string
  contributed_on: string
  note: string | null
  transaction_id: number | null
}

export interface RecurringRule {
  id: number
  name: string
  type: TransactionType
  amount_minor: number
  currency_code: string
  formatted_amount: string
  merchant: string | null
  note: string | null
  frequency: string
  frequency_label: string
  interval: number
  by_month_day: number | null
  by_weekday: number | null
  start_date: string
  end_date: string | null
  occurrence_limit: number | null
  occurrences_created: number
  next_run_on: string | null
  last_run_on: string | null
  upcoming_dates: string[]
  auto_post: boolean
  is_active: boolean
  account_id: number
  account?: Account
  transfer_account_id: number | null
  category_id: number | null
  category?: Category
}

export interface CashFlow {
  income_minor: number
  expense_minor: number
  saved_minor: number
  net_minor: number
  formatted: { income: string; expense: string; saved: string; net: string }
}

export interface Balances {
  available_minor: number
  owed_minor: number
  net_worth_minor: number
  formatted: { available: string; owed: string; net_worth: string }
  accounts: Array<{
    id: number
    name: string
    type: AccountType
    balance_minor: number
    formatted: string
    is_liability: boolean
  }>
}

export interface CategoryBreakdown {
  category_id: number | null
  name: string
  color: string | null
  icon: string | null
  group: string | null
  amount_minor: number
  formatted: string
  transactions: number
  share: number
}

export interface MonthlyTrend {
  period: string
  label: string
  income_minor: number
  expense_minor: number
  saved_minor: number
  net_minor: number
  currency: string
}

export interface MerchantTotal {
  merchant: string
  amount_minor: number
  formatted: string
  visits: number
  average_minor: number
}

export interface Dashboard {
  period: { from: string; to: string; label: string }
  currency: string
  cash_flow: CashFlow
  balances: Balances
  budget: {
    overall: BudgetView['meta']['overall']
    categories: BudgetLine[]
    unbudgeted_minor: number
    uncategorized_minor: number
  } | null
  upcoming_bills: Array<{
    id: number
    name: string
    amount_minor: number
    formatted: string
    due_date: string
    days_until_due: number
    is_overdue: boolean
    category: string | null
  }>
  savings_goals: Array<{
    id: number
    name: string
    target_minor: number
    saved_minor: number
    remaining_minor: number
    progress: number
    target_date: string | null
    formatted: { target: string; saved: string }
  }>
  recent_transactions: Transaction[]
  top_categories: CategoryBreakdown[]
}

export interface AppNotification {
  id: string
  type: string
  title: string | null
  body: string | null
  data: Record<string, any>
  read: boolean
  read_at: string | null
  created_at: string | null
}

export interface OnboardingOptions {
  currencies: Array<{ code: string; name: string; symbol: string; exponent: number }>
  account_types: Array<{ value: AccountType; label: string; icon: string; is_liability: boolean }>
  catalog: Array<{
    type: CategoryType
    groups: Array<{
      name: string
      icon: string
      color: string
      categories: Array<{ name: string; icon: string; recommended: boolean }>
    }>
  }>
  recommended_categories: string[]
  detected_currency: string
  completed: boolean
}

export interface CategorySuggestion {
  category: Category | null
  confidence: number
  source: string
  proposal: { name: string; group: string; icon: string; color: string } | null
}

export interface TransactionFilters {
  q?: string
  type?: TransactionType | 'all' | 'cash_flow'
  account_id?: number[]
  category_id?: number[]
  tag_id?: number[]
  from?: string
  to?: string
  min_amount?: string
  max_amount?: string
  has_receipt?: boolean
  uncategorized?: boolean
  sort?: 'occurred_on' | 'amount' | 'merchant' | 'created_at'
  direction?: 'asc' | 'desc'
  per_page?: number
  page?: number
}

export interface ApiErrorBody {
  message: string
  errors?: Record<string, string[]>
}
