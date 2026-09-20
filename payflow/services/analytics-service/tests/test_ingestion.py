from datetime import UTC

import pytest
from sqlalchemy import func, select

from app.ingestion import event_from_bytes, ingest_event, ingest_note_files, parse_instant
from app.models import AnalystNote, FraudFact, PaymentFact, RawEvent, WalletLedgerFact


async def test_event_ingestion_is_idempotent(session):
    event = {
        "eventId": "event-1",
        "paymentId": "payment-1",
        "userId": "user-1",
        "amountMinor": 1499,
        "currency": "INR",
        "merchantId": "AMZN-IN",
        "country": "IN",
        "occurredAt": "2026-09-20T00:00:00Z",
    }

    assert await ingest_event(session, topic="payment.initiated", payload=event, offset=10)
    assert not await ingest_event(session, topic="payment.initiated", payload=event, offset=10)
    assert await session.scalar(select(func.count()).select_from(RawEvent)) == 1
    payment = await session.get(PaymentFact, "payment-1")
    assert payment.amount_minor == 1499
    assert payment.status == "INITIATED"


async def test_wallet_event_projects_ledger(session):
    event = {
        "eventId": "ledger-1",
        "walletId": "wallet-1",
        "userId": "user-1",
        "paymentId": "payment-1",
        "entryType": "CAPTURE",
        "amountMinor": 1499,
        "availableBalanceMinor": 8501,
        "reservedBalanceMinor": 0,
        "occurredAt": "2026-09-20T00:01:00Z",
    }
    await ingest_event(session, topic="wallet.ledger", payload=event)
    assert (await session.get(WalletLedgerFact, "ledger-1")).entry_type == "CAPTURE"


async def test_markdown_notes_are_idempotent(session, tmp_path):
    note = tmp_path / "case.md"
    note.write_text(
        "payment_id: payment-1\nmerchant_id: WIRE-US\nreason_code: EDD\n\nFree-form analyst text.",
        encoding="utf-8",
    )
    assert await ingest_note_files(session, str(tmp_path)) == 1
    assert await ingest_note_files(session, str(tmp_path)) == 0
    stored = (await session.scalars(select(AnalystNote))).one()
    assert stored.payment_id == "payment-1"
    assert "Free-form" in stored.note_text


async def test_fraud_projection_and_payment_transition(session):
    await ingest_event(
        session,
        topic="fraud.checked",
        payload={
            "eventId": "fraud-1",
            "paymentId": "payment-2",
            "userId": "user-1",
            "amountMinor": 5000001,
            "riskScore": 0.7,
            "decision": "REVIEW",
            "explanation": "because of HIGH_AMOUNT, GEO_ANOMALY",
            "occurredAt": "2026-09-20T00:02:00Z",
        },
    )
    await ingest_event(
        session,
        topic="payment.completed",
        payload={
            "eventId": "completed-1",
            "paymentId": "payment-2",
            "userId": "user-1",
            "walletId": "wallet-1",
            "amountMinor": 5000001,
            "currency": "INR",
            "merchantId": "WIRE-US",
            "occurredAt": "2026-09-20T00:03:00Z",
        },
        offset=1,
    )
    assert (await session.get(FraudFact, "payment-2")).decision == "REVIEW"
    assert (await session.get(PaymentFact, "payment-2")).status == "COMPLETED"


def test_wire_payload_parser_rejects_non_object_json():
    assert event_from_bytes(b'{"eventId":"one"}') == {"eventId": "one"}
    with pytest.raises(ValueError):
        event_from_bytes(b"[]")
    assert parse_instant(None).tzinfo is not None


async def test_late_initiated_event_cannot_regress_terminal_status(session):
    await ingest_event(
        session,
        topic="payment.completed",
        payload={
            "eventId": "completed-first",
            "paymentId": "payment-late",
            "userId": "user-1",
            "amountMinor": 2500,
            "currency": "INR",
            "merchantId": "LATE-DEMO",
            "occurredAt": "2026-09-20T00:05:00Z",
        },
    )
    await ingest_event(
        session,
        topic="payment.initiated",
        payload={
            "eventId": "initiated-late",
            "paymentId": "payment-late",
            "userId": "user-1",
            "amountMinor": 2500,
            "currency": "INR",
            "merchantId": "LATE-DEMO",
            "country": "IN",
            "occurredAt": "2026-09-20T00:01:00Z",
        },
        offset=1,
    )

    payment = await session.get(PaymentFact, "payment-late")
    assert payment.status == "COMPLETED"
    assert payment.country == "IN"
    assert payment.initiated_at.replace(tzinfo=UTC) == parse_instant("2026-09-20T00:01:00Z")
