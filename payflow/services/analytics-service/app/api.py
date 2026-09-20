import csv
import io
from datetime import date

from fastapi import APIRouter, Depends, Query, Request
from fastapi.responses import StreamingResponse
from sqlalchemy import and_, func, select, text
from sqlalchemy.ext.asyncio import AsyncSession

from .database import session_dependency
from .ingestion import ingest_note_files
from .models import (
    AnalystNote,
    DailyKpi,
    FraudFact,
    PaymentFact,
    RawEvent,
    RiskRuleMart,
    WalletLedgerFact,
)
from .security import Principal, require_admin
from .transforms import refresh_marts

router = APIRouter()


@router.get("/health", tags=["platform"])
async def health() -> dict[str, str]:
    return {"status": "UP", "service": "analytics-service"}


@router.get("/ready", tags=["platform"])
async def ready(session: AsyncSession = Depends(session_dependency)) -> dict[str, str]:
    await session.execute(text("SELECT 1"))
    return {"status": "READY"}


@router.get("/api/v1/analytics/kpis", tags=["analytics"])
async def kpis(
    from_day: date | None = Query(None, alias="from"),
    to_day: date | None = Query(None, alias="to"),
    _: Principal = Depends(require_admin),
    session: AsyncSession = Depends(session_dependency),
) -> list[dict]:
    query = select(DailyKpi).order_by(DailyKpi.day.desc())
    if from_day:
        query = query.where(DailyKpi.day >= from_day)
    if to_day:
        query = query.where(DailyKpi.day <= to_day)
    rows = (await session.scalars(query)).all()
    return [
        {
            "day": row.day,
            "currency": row.currency,
            "initiatedCount": row.initiated_count,
            "completedCount": row.completed_count,
            "reviewCount": row.review_count,
            "failedCount": row.failed_count,
            "totalVolumeMinor": row.total_volume_minor,
            "completedVolumeMinor": row.completed_volume_minor,
            "captureRate": row.capture_rate,
            "reviewRate": row.review_rate,
            "refreshedAt": row.refreshed_at,
        }
        for row in rows
    ]


@router.get("/api/v1/analytics/risk-summary", tags=["analytics"])
async def risk_summary(
    _: Principal = Depends(require_admin),
    session: AsyncSession = Depends(session_dependency),
) -> dict:
    rows = (
        await session.scalars(
            select(RiskRuleMart).order_by(RiskRuleMart.day.desc(), RiskRuleMart.assessment_count.desc())
        )
    ).all()
    decisions = (
        await session.execute(select(FraudFact.decision, func.count()).group_by(FraudFact.decision))
    ).all()
    return {
        "decisions": {decision: count for decision, count in decisions},
        "rules": [
            {
                "day": row.day,
                "rule": row.rule,
                "assessmentCount": row.assessment_count,
                "averageScore": row.average_score,
                "reviewCount": row.review_count,
                "rejectCount": row.reject_count,
            }
            for row in rows
        ],
    }


@router.get("/api/v1/analytics/ledger-integrity", tags=["analytics"])
async def ledger_integrity(
    _: Principal = Depends(require_admin),
    session: AsyncSession = Depends(session_dependency),
) -> dict:
    capture = (
        select(
            WalletLedgerFact.payment_id.label("payment_id"),
            func.sum(WalletLedgerFact.amount_minor).label("captured_minor"),
        )
        .where(WalletLedgerFact.entry_type == "CAPTURE")
        .group_by(WalletLedgerFact.payment_id)
        .subquery()
    )
    rows = (
        await session.execute(
            select(PaymentFact.payment_id, PaymentFact.amount_minor, capture.c.captured_minor)
            .outerjoin(capture, PaymentFact.payment_id == capture.c.payment_id)
            .where(
                and_(
                    PaymentFact.status == "COMPLETED",
                    (capture.c.captured_minor.is_(None))
                    | (capture.c.captured_minor != PaymentFact.amount_minor),
                )
            )
        )
    ).all()
    return {
        "mismatchCount": len(rows),
        "mismatches": [
            {
                "paymentId": payment_id,
                "expectedCaptureMinor": expected,
                "actualCaptureMinor": actual or 0,
            }
            for payment_id, expected, actual in rows
        ],
    }


@router.get("/api/v1/analytics/pipeline-status", tags=["analytics"])
async def pipeline_status(
    request: Request,
    _: Principal = Depends(require_admin),
    session: AsyncSession = Depends(session_dependency),
) -> dict:
    counts = {}
    for name, model in (
        ("rawEvents", RawEvent),
        ("payments", PaymentFact),
        ("fraudAssessments", FraudFact),
        ("ledgerEntries", WalletLedgerFact),
        ("analystNotes", AnalystNote),
    ):
        counts[name] = await session.scalar(select(func.count()).select_from(model))
    state = request.app.state.pipeline.state
    return {
        **counts,
        "kafkaConnected": state.kafka_connected,
        "processedEvents": state.processed_events,
        "lastEventAt": state.last_event_at,
        "lastRefreshAt": state.last_refresh_at,
        "lastError": state.last_error,
    }


@router.post("/api/v1/analytics/refresh", tags=["analytics"])
async def refresh(
    request: Request,
    _: Principal = Depends(require_admin),
    session: AsyncSession = Depends(session_dependency),
) -> dict:
    notes = await ingest_note_files(session, request.app.state.settings.inbox_directory)
    marts = await refresh_marts(session)
    return {"notesIngested": notes, **marts}


@router.get("/api/v1/analytics/exports/daily-kpis.csv", tags=["exports"])
async def export_daily_kpis(
    _: Principal = Depends(require_admin),
    session: AsyncSession = Depends(session_dependency),
) -> StreamingResponse:
    rows = (await session.scalars(select(DailyKpi).order_by(DailyKpi.day))).all()
    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow(
        [
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
    )
    for row in rows:
        writer.writerow(
            [
                row.day,
                row.currency,
                row.initiated_count,
                row.completed_count,
                row.review_count,
                row.failed_count,
                row.total_volume_minor,
                row.completed_volume_minor,
                row.capture_rate,
                row.review_rate,
            ]
        )
    return StreamingResponse(
        iter([output.getvalue()]),
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=paynexus-daily-kpis.csv"},
    )
