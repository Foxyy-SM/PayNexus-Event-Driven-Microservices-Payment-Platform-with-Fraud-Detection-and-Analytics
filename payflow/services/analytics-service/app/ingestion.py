import hashlib
import json
import re
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from sqlalchemy.ext.asyncio import AsyncSession

from .models import AnalystNote, FraudFact, PaymentFact, RawEvent, WalletLedgerFact

PAYMENT_STATUS_TOPICS = {
    "payment.initiated": "INITIATED",
    "payment.reserved": "RESERVED",
    "payment.pending-review": "PENDING_REVIEW",
    "payment.completed": "COMPLETED",
    "payment.failed": "FAILED",
}
STATUS_RANK = {
    "INITIATED": 10,
    "RESERVED": 20,
    "CAPTURING": 30,
    "PENDING_REVIEW": 30,
    "COMPLETED": 40,
    "FAILED": 40,
    "REJECTED": 40,
}


def parse_instant(value: str | None) -> datetime:
    if not value:
        return datetime.now(UTC)
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


async def ingest_event(
    session: AsyncSession,
    *,
    topic: str,
    payload: dict[str, Any],
    event_key: str | None = None,
    partition: int = 0,
    offset: int = 0,
) -> bool:
    event_id = str(payload.get("eventId") or f"{topic}:{partition}:{offset}")
    if await session.get(RawEvent, event_id):
        return False

    session.add(
        RawEvent(
            event_id=event_id,
            topic=topic,
            event_key=event_key,
            partition=partition,
            offset=offset,
            occurred_at=parse_instant(payload.get("occurredAt")),
            payload=payload,
        )
    )
    if topic in PAYMENT_STATUS_TOPICS:
        await _upsert_payment(session, topic, payload)
    elif topic == "fraud.checked":
        await _upsert_fraud(session, payload)
    elif topic == "wallet.ledger":
        await _insert_ledger(session, payload)
    await session.commit()
    return True


async def _upsert_payment(session: AsyncSession, topic: str, payload: dict[str, Any]) -> None:
    payment_id = str(payload["paymentId"])
    incoming_status = str(payload.get("status") or PAYMENT_STATUS_TOPICS[topic])
    occurred_at = parse_instant(payload.get("occurredAt"))
    fact = await session.get(PaymentFact, payment_id)
    if fact is None:
        fact = PaymentFact(payment_id=payment_id, status=incoming_status, updated_at=occurred_at)
        session.add(fact)
    fact.user_id = str(payload.get("userId") or fact.user_id or "") or None
    fact.amount_minor = payload.get("amountMinor", fact.amount_minor)
    fact.currency = payload.get("currency", fact.currency)
    fact.merchant_id = payload.get("merchantId", fact.merchant_id)
    fact.country = payload.get("country", fact.country)
    current_rank = STATUS_RANK.get(fact.status, 0)
    incoming_rank = STATUS_RANK.get(incoming_status, 0)
    current_time = fact.updated_at
    if current_time.tzinfo is None:
        current_time = current_time.replace(tzinfo=UTC)
    if incoming_rank > current_rank or (incoming_rank == current_rank and occurred_at >= current_time):
        fact.status = incoming_status
    if topic == "payment.initiated":
        fact.initiated_at = occurred_at
    if occurred_at > current_time:
        fact.updated_at = occurred_at


async def _upsert_fraud(session: AsyncSession, payload: dict[str, Any]) -> None:
    payment_id = str(payload["paymentId"])
    fact = await session.get(FraudFact, payment_id)
    if fact is None:
        fact = FraudFact(
            payment_id=payment_id,
            user_id=str(payload.get("userId") or "") or None,
            amount_minor=payload.get("amountMinor"),
            risk_score=float(payload["riskScore"]),
            decision=str(payload["decision"]),
            explanation=str(payload.get("explanation") or ""),
            occurred_at=parse_instant(payload.get("occurredAt")),
        )
        session.add(fact)
        return
    fact.risk_score = float(payload["riskScore"])
    fact.decision = str(payload["decision"])
    fact.explanation = str(payload.get("explanation") or "")
    fact.occurred_at = parse_instant(payload.get("occurredAt"))


async def _insert_ledger(session: AsyncSession, payload: dict[str, Any]) -> None:
    event_id = str(payload["eventId"])
    if await session.get(WalletLedgerFact, event_id):
        return
    session.add(
        WalletLedgerFact(
            event_id=event_id,
            payment_id=str(payload.get("paymentId") or "") or None,
            wallet_id=str(payload["walletId"]),
            user_id=str(payload["userId"]),
            entry_type=str(payload["entryType"]),
            amount_minor=int(payload["amountMinor"]),
            available_balance_minor=int(payload["availableBalanceMinor"]),
            reserved_balance_minor=int(payload["reservedBalanceMinor"]),
            occurred_at=parse_instant(payload.get("occurredAt")),
        )
    )


NOTE_FIELD = re.compile(r"^(payment_id|merchant_id|reason_code):\s*(.+)$", re.MULTILINE | re.I)


async def ingest_note_files(session: AsyncSession, inbox: str) -> int:
    inserted = 0
    for path in sorted(Path(inbox).glob("*.md")):
        text = path.read_text(encoding="utf-8")
        fields = {key.lower(): value.strip() for key, value in NOTE_FIELD.findall(text)}
        note_id = hashlib.sha256(f"{path.name}\n{text}".encode()).hexdigest()
        if await session.get(AnalystNote, note_id):
            continue
        body = re.sub(NOTE_FIELD, "", text).strip().lstrip("#").strip()
        session.add(
            AnalystNote(
                note_id=note_id,
                payment_id=fields.get("payment_id"),
                merchant_id=fields.get("merchant_id"),
                reason_code=fields.get("reason_code"),
                note_text=body,
                source_file=path.name,
            )
        )
        inserted += 1
    await session.commit()
    return inserted


def event_from_bytes(value: bytes) -> dict[str, Any]:
    decoded = json.loads(value.decode("utf-8"))
    if not isinstance(decoded, dict):
        raise ValueError("Kafka event must be a JSON object")
    return decoded
