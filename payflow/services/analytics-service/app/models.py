from datetime import UTC, date, datetime

from sqlalchemy import JSON, BigInteger, Date, DateTime, Float, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


def utcnow() -> datetime:
    return datetime.now(UTC)


class Base(DeclarativeBase):
    pass


class RawEvent(Base):
    __tablename__ = "stg_raw_events"

    event_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    topic: Mapped[str] = mapped_column(String(100), index=True)
    event_key: Mapped[str | None] = mapped_column(String(255))
    partition: Mapped[int] = mapped_column(Integer)
    offset: Mapped[int] = mapped_column(BigInteger)
    occurred_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), index=True)
    ingested_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    payload: Mapped[dict] = mapped_column(JSON)

    __table_args__ = (UniqueConstraint("topic", "partition", "offset", name="uq_raw_topic_offset"),)


class PaymentFact(Base):
    __tablename__ = "stg_payments"

    payment_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[str | None] = mapped_column(String(64), index=True)
    merchant_id: Mapped[str | None] = mapped_column(String(255), index=True)
    amount_minor: Mapped[int | None] = mapped_column(BigInteger)
    currency: Mapped[str | None] = mapped_column(String(3))
    country: Mapped[str | None] = mapped_column(String(2))
    status: Mapped[str] = mapped_column(String(40), index=True)
    initiated_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class FraudFact(Base):
    __tablename__ = "stg_fraud_assessments"

    payment_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[str | None] = mapped_column(String(64), index=True)
    amount_minor: Mapped[int | None] = mapped_column(BigInteger)
    risk_score: Mapped[float] = mapped_column(Float)
    decision: Mapped[str] = mapped_column(String(20), index=True)
    explanation: Mapped[str] = mapped_column(Text, default="")
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class WalletLedgerFact(Base):
    __tablename__ = "stg_wallet_ledger"

    event_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    payment_id: Mapped[str | None] = mapped_column(String(64), index=True)
    wallet_id: Mapped[str] = mapped_column(String(64))
    user_id: Mapped[str] = mapped_column(String(64))
    entry_type: Mapped[str] = mapped_column(String(20), index=True)
    amount_minor: Mapped[int] = mapped_column(BigInteger)
    available_balance_minor: Mapped[int] = mapped_column(BigInteger)
    reserved_balance_minor: Mapped[int] = mapped_column(BigInteger)
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)


class AnalystNote(Base):
    __tablename__ = "stg_analyst_notes"

    note_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    payment_id: Mapped[str | None] = mapped_column(String(64), index=True)
    merchant_id: Mapped[str | None] = mapped_column(String(255))
    reason_code: Mapped[str | None] = mapped_column(String(80))
    note_text: Mapped[str] = mapped_column(Text)
    source_file: Mapped[str] = mapped_column(String(255))
    ingested_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class DailyKpi(Base):
    __tablename__ = "mart_daily_kpis"

    day: Mapped[date] = mapped_column(Date, primary_key=True)
    currency: Mapped[str] = mapped_column(String(3), primary_key=True)
    initiated_count: Mapped[int] = mapped_column(Integer, default=0)
    completed_count: Mapped[int] = mapped_column(Integer, default=0)
    review_count: Mapped[int] = mapped_column(Integer, default=0)
    failed_count: Mapped[int] = mapped_column(Integer, default=0)
    total_volume_minor: Mapped[int] = mapped_column(BigInteger, default=0)
    completed_volume_minor: Mapped[int] = mapped_column(BigInteger, default=0)
    capture_rate: Mapped[float] = mapped_column(Float, default=0)
    review_rate: Mapped[float] = mapped_column(Float, default=0)
    refreshed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class RiskRuleMart(Base):
    __tablename__ = "mart_risk_by_rule"

    day: Mapped[date] = mapped_column(Date, primary_key=True)
    rule: Mapped[str] = mapped_column(String(100), primary_key=True)
    assessment_count: Mapped[int] = mapped_column(Integer)
    average_score: Mapped[float] = mapped_column(Float)
    review_count: Mapped[int] = mapped_column(Integer)
    reject_count: Mapped[int] = mapped_column(Integer)
    refreshed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
