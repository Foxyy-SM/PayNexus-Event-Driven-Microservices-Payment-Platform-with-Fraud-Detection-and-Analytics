from datetime import UTC, date, datetime

from app.models import DailyKpi, FraudFact, PaymentFact, WalletLedgerFact


async def test_health_is_public(client):
    response = await client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"


async def test_kpis_returns_power_bi_ready_contract(client, session):
    session.add(
        DailyKpi(
            day=date(2026, 9, 20),
            currency="INR",
            initiated_count=10,
            completed_count=8,
            review_count=1,
            failed_count=1,
            total_volume_minor=100_000,
            completed_volume_minor=80_000,
            capture_rate=0.8,
            review_rate=0.1,
        )
    )
    await session.commit()
    response = await client.get("/api/v1/analytics/kpis")
    assert response.status_code == 200
    assert response.json()[0]["totalVolumeMinor"] == 100_000
    assert response.json()[0]["captureRate"] == 0.8


async def test_ledger_integrity_detects_missing_capture(client, session):
    now = datetime.now(UTC)
    session.add(
        PaymentFact(
            payment_id="bad-payment",
            amount_minor=2500,
            currency="INR",
            status="COMPLETED",
            updated_at=now,
        )
    )
    session.add(
        PaymentFact(
            payment_id="good-payment",
            amount_minor=1000,
            currency="INR",
            status="COMPLETED",
            updated_at=now,
        )
    )
    session.add(
        WalletLedgerFact(
            event_id="capture-1",
            payment_id="good-payment",
            wallet_id="wallet-1",
            user_id="user-1",
            entry_type="CAPTURE",
            amount_minor=1000,
            available_balance_minor=0,
            reserved_balance_minor=0,
            occurred_at=now,
        )
    )
    await session.commit()
    response = await client.get("/api/v1/analytics/ledger-integrity")
    assert response.status_code == 200
    assert response.json()["mismatchCount"] == 1
    assert response.json()["mismatches"][0]["paymentId"] == "bad-payment"


async def test_csv_export_has_stable_headers(client, session):
    session.add(
        DailyKpi(
            day=date(2026, 9, 20),
            currency="INR",
            initiated_count=2,
            completed_count=1,
            review_count=1,
            failed_count=0,
            total_volume_minor=3000,
            completed_volume_minor=1000,
            capture_rate=0.5,
            review_rate=0.5,
        )
    )
    await session.commit()
    response = await client.get("/api/v1/analytics/exports/daily-kpis.csv")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/csv")
    assert response.text.startswith("day,currency,initiated_count,completed_count")
    assert "2026-09-20,INR,2,1,1,0,3000,1000,0.5,0.5" in response.text


async def test_risk_summary_and_pipeline_status(client, session):
    session.add(
        FraudFact(
            payment_id="risk-1",
            risk_score=0.7,
            decision="REVIEW",
            explanation="HIGH_AMOUNT",
            occurred_at=datetime.now(UTC),
        )
    )
    await session.commit()

    risk = await client.get("/api/v1/analytics/risk-summary")
    status = await client.get("/api/v1/analytics/pipeline-status")

    assert risk.status_code == 200
    assert risk.json()["decisions"] == {"REVIEW": 1}
    assert status.status_code == 200
    assert status.json()["fraudAssessments"] == 1
    assert status.json()["kafkaConnected"] is True


async def test_ready_and_manual_refresh(client):
    assert (await client.get("/ready")).status_code == 200
    response = await client.post("/api/v1/analytics/refresh")
    assert response.status_code == 200
    assert response.json() == {"notesIngested": 0, "daily_kpis": 0, "risk_rules": 0}
