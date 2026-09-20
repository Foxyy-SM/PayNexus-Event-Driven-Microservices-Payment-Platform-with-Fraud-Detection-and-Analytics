from datetime import UTC, datetime

import pytest
from sqlalchemy import select

from app.models import DailyKpi, FraudFact, PaymentFact, RiskRuleMart
from app.transforms import daily_kpis_frame, extract_rules, refresh_marts, risk_rules_frame


def test_daily_kpis_use_minor_units_and_terminal_statuses():
    rows = [
        {
            "payment_id": "p1",
            "amount_minor": 1000,
            "currency": "INR",
            "status": "COMPLETED",
            "initiated_at": "2026-09-20T01:00:00Z",
            "updated_at": "2026-09-20T01:01:00Z",
        },
        {
            "payment_id": "p2",
            "amount_minor": 2000,
            "currency": "INR",
            "status": "PENDING_REVIEW",
            "initiated_at": "2026-09-20T02:00:00Z",
            "updated_at": "2026-09-20T02:01:00Z",
        },
    ]
    result = daily_kpis_frame(rows).iloc[0]
    assert result["total_volume_minor"] == 3000
    assert result["completed_volume_minor"] == 1000
    assert result["capture_rate"] == pytest.approx(0.5)
    assert result["review_rate"] == pytest.approx(0.5)


@pytest.mark.parametrize(
    ("explanation", "expected"),
    [
        ("because of HIGH_AMOUNT, GEO_ANOMALY", ["HIGH_AMOUNT", "GEO_ANOMALY"]),
        ("No elevated rule hits", ["NO_ELEVATED_RULE"]),
    ],
)
def test_rule_extraction(explanation, expected):
    assert extract_rules(explanation) == expected


def test_risk_rule_mart_explodes_multiple_rules():
    result = risk_rules_frame(
        [
            {
                "payment_id": "p1",
                "risk_score": 0.7,
                "decision": "REVIEW",
                "explanation": "HIGH_AMOUNT and GEO_ANOMALY",
                "occurred_at": datetime(2026, 9, 20, tzinfo=UTC),
            }
        ]
    )
    assert set(result["rule"]) == {"HIGH_AMOUNT", "GEO_ANOMALY"}
    assert result["review_count"].sum() == 2


async def test_refresh_marts_replaces_materialized_rows(session):
    now = datetime(2026, 9, 20, tzinfo=UTC)
    session.add(
        PaymentFact(
            payment_id="p1",
            amount_minor=5000,
            currency="INR",
            status="COMPLETED",
            initiated_at=now,
            updated_at=now,
        )
    )
    session.add(
        FraudFact(
            payment_id="p1",
            risk_score=0.4,
            decision="APPROVE",
            explanation="because of HIGH_AMOUNT",
            occurred_at=now,
        )
    )
    await session.commit()

    result = await refresh_marts(session)

    assert result == {"daily_kpis": 1, "risk_rules": 1}
    assert (await session.scalars(select(DailyKpi))).one().completed_volume_minor == 5000
    assert (await session.scalars(select(RiskRuleMart))).one().rule == "HIGH_AMOUNT"
