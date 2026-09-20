# PayNexus Power BI model

This folder documents a small business-reporting layer over the analytics marts. A binary `.pbix` is not
committed because it is opaque to code review and cannot be validated in Linux CI. Build the report from
the version-controlled source contract below, then optionally publish screenshots or the `.pbix` as a
GitHub release artifact.

## Import options

### CSV API (simplest recruiter demo)

1. Obtain an admin token.
2. Download `GET /api/v1/analytics/exports/daily-kpis.csv`.
3. In Power BI Desktop choose **Get data → Text/CSV**.
4. Set `day` to Date; counts and minor-unit columns to Whole number; rates to Decimal number.
5. Keep `currency` on every monetary visual so unlike currencies are never added together.

For refreshable authenticated HTTP imports, use **Get data → Web → Advanced**, supply the endpoint URL,
and add `Authorization: Bearer <token>` as an HTTP header. Local demo tokens expire, so a scheduled
production refresh should use a dedicated confidential BI client rather than storing a user token.

### PostgreSQL marts (recommended semantic model)

Connect to PostgreSQL and import:

- `mart_daily_kpis`
- `mart_risk_by_rule`
- optionally `stg_analyst_notes`

Use Import mode for the portfolio demo. In a managed deployment these same portable mart queries can be
materialized in Snowflake and Power BI can point to that warehouse.

## Star schema

Create a Date table and relate:

```DAX
Date = CALENDAR(MIN(mart_daily_kpis[day]), MAX(mart_daily_kpis[day]))
```

- `Date[Date]` 1 → many `mart_daily_kpis[day]`
- `Date[Date]` 1 → many `mart_risk_by_rule[day]`

Suggested measures:

```DAX
Total Volume Minor = SUM(mart_daily_kpis[total_volume_minor])
Completed Volume Minor = SUM(mart_daily_kpis[completed_volume_minor])
Payments = SUM(mart_daily_kpis[initiated_count])
Completed Payments = SUM(mart_daily_kpis[completed_count])
Reviewed Payments = SUM(mart_daily_kpis[review_count])
Failed Payments = SUM(mart_daily_kpis[failed_count])
Capture Rate = DIVIDE([Completed Payments], [Payments])
Review Rate = DIVIDE([Reviewed Payments], [Payments])
Average Ticket Minor = DIVIDE([Total Volume Minor], [Payments])
```

Format rate measures as percentages. Display money only after applying a selected currency/exponent; do
not blindly divide all minor-unit values by 100 because ISO 4217 exponents vary.

## One-page report

Recommended visuals:

1. Cards: Total Volume, Payments, Capture Rate, Review Rate.
2. Line chart: daily completed volume and payment count.
3. Stacked column: completed/reviewed/failed by day.
4. Bar chart: top fraud rules by assessment count.
5. Matrix: rule, average score, reviews, rejects.
6. Slicers: date and currency.

The reporting boundary is deliberate: Grafana shows runtime reliability and latency; Power BI shows
business throughput and risk operations.
