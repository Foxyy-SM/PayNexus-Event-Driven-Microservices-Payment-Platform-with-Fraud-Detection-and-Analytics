from datetime import UTC, datetime

import pandas as pd
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from .models import DailyKpi, FraudFact, PaymentFact, RiskRuleMart

KNOWN_RULES = ("HIGH_AMOUNT", "CRITICAL_AMOUNT", "VELOCITY_10M", "GEO_ANOMALY")


def daily_kpis_frame(payments: list[dict]) -> pd.DataFrame:
    columns = [
        "day",
        "currency",
        "initiated_count",
        "completed_count",
        "review_count",
        "failed_count",
        "total_volume_minor",
        "completed_volume_minor",
        "capture_rate",
        "review_rate",
    ]
    if not payments:
        return pd.DataFrame(columns=columns)
    frame = pd.DataFrame(payments)
    frame["event_time"] = pd.to_datetime(frame["initiated_at"].fillna(frame["updated_at"]), utc=True)
    frame["day"] = frame["event_time"].dt.date
    frame["amount_minor"] = frame["amount_minor"].fillna(0).astype("int64")
    frame["currency"] = frame["currency"].fillna("UNKNOWN")
    rows = []
    for (day, currency), group in frame.groupby(["day", "currency"], sort=True):
        count = len(group)
        completed = group["status"].eq("COMPLETED")
        reviewed = group["status"].eq("PENDING_REVIEW")
        failed = group["status"].isin(["FAILED", "REJECTED"])
        rows.append(
            {
                "day": day,
                "currency": currency,
                "initiated_count": count,
                "completed_count": int(completed.sum()),
                "review_count": int(reviewed.sum()),
                "failed_count": int(failed.sum()),
                "total_volume_minor": int(group["amount_minor"].sum()),
                "completed_volume_minor": int(group.loc[completed, "amount_minor"].sum()),
                "capture_rate": round(float(completed.sum() / count), 4),
                "review_rate": round(float(reviewed.sum() / count), 4),
            }
        )
    return pd.DataFrame(rows, columns=columns)


def extract_rules(explanation: str) -> list[str]:
    hits = [rule for rule in KNOWN_RULES if rule in explanation.upper()]
    return hits or ["NO_ELEVATED_RULE"]


def risk_rules_frame(assessments: list[dict]) -> pd.DataFrame:
    columns = ["day", "rule", "assessment_count", "average_score", "review_count", "reject_count"]
    if not assessments:
        return pd.DataFrame(columns=columns)
    frame = pd.DataFrame(assessments)
    frame["day"] = pd.to_datetime(frame["occurred_at"], utc=True).dt.date
    frame["rule"] = frame["explanation"].fillna("").map(extract_rules)
    frame = frame.explode("rule")
    grouped = frame.groupby(["day", "rule"], sort=True)
    result = grouped.agg(
        assessment_count=("payment_id", "count"),
        average_score=("risk_score", "mean"),
        review_count=("decision", lambda values: int(values.eq("REVIEW").sum())),
        reject_count=("decision", lambda values: int(values.eq("REJECT").sum())),
    ).reset_index()
    result["average_score"] = result["average_score"].round(4)
    return result[columns]


async def refresh_marts(session: AsyncSession) -> dict[str, int]:
    payments = [
        {
            "payment_id": row.payment_id,
            "amount_minor": row.amount_minor,
            "currency": row.currency,
            "status": row.status,
            "initiated_at": row.initiated_at,
            "updated_at": row.updated_at,
        }
        for row in (await session.scalars(select(PaymentFact))).all()
    ]
    assessments = [
        {
            "payment_id": row.payment_id,
            "risk_score": row.risk_score,
            "decision": row.decision,
            "explanation": row.explanation,
            "occurred_at": row.occurred_at,
        }
        for row in (await session.scalars(select(FraudFact))).all()
    ]
    kpis = daily_kpis_frame(payments)
    risks = risk_rules_frame(assessments)
    now = datetime.now(UTC)

    await session.execute(delete(DailyKpi))
    await session.execute(delete(RiskRuleMart))
    for row in kpis.to_dict("records"):
        session.add(DailyKpi(**row, refreshed_at=now))
    for row in risks.to_dict("records"):
        session.add(RiskRuleMart(**row, refreshed_at=now))
    await session.commit()
    return {"daily_kpis": len(kpis), "risk_rules": len(risks)}
