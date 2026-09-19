# International high-value transfers

International payments above ₹2,00,000 require enhanced due diligence.

Policy:
- Score the transaction as HIGH_RISK when the destination country differs from the customer's KYC country.
- Require a REVIEW decision rather than automatic APPROVE.
- REJECT if velocity in the last 10 minutes exceeds 5 payments AND the amount is above ₹50,000.
- Notify the compliance queue and retain the risk explanation for 7 years.
