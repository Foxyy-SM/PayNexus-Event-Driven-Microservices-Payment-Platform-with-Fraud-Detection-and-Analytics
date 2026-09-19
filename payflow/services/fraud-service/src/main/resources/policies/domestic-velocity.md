# Domestic velocity and amount rules

Domestic INR payments:
- Amounts above ₹50,000 are flagged HIGH_AMOUNT.
- More than 5 transactions in 10 minutes trigger VELOCITY_10M.
- Combined HIGH_AMOUNT + VELOCITY_10M should move the decision toward REJECT.
- First-time merchants with amounts above ₹25,000 should be REVIEW.
