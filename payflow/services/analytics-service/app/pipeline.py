import asyncio
import logging
from dataclasses import dataclass
from datetime import UTC, datetime

from aiokafka import AIOKafkaConsumer

from .config import Settings
from .database import SessionLocal
from .ingestion import event_from_bytes, ingest_event, ingest_note_files
from .transforms import refresh_marts

LOGGER = logging.getLogger(__name__)
TOPICS = (
    "payment.initiated",
    "payment.reserved",
    "payment.pending-review",
    "payment.completed",
    "payment.failed",
    "fraud.checked",
    "wallet.ledger",
)


@dataclass
class PipelineState:
    kafka_connected: bool = False
    last_event_at: datetime | None = None
    last_refresh_at: datetime | None = None
    last_error: str | None = None
    processed_events: int = 0


class AnalyticsPipeline:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.state = PipelineState()
        self._tasks: list[asyncio.Task] = []
        self._consumer: AIOKafkaConsumer | None = None

    async def start(self) -> None:
        async with SessionLocal() as session:
            await ingest_note_files(session, self.settings.inbox_directory)
            await refresh_marts(session)
        if self.settings.kafka_enabled:
            self._tasks.append(asyncio.create_task(self._consume_forever(), name="analytics-kafka"))
        self._tasks.append(asyncio.create_task(self._refresh_forever(), name="analytics-refresh"))

    async def stop(self) -> None:
        for task in self._tasks:
            task.cancel()
        await asyncio.gather(*self._tasks, return_exceptions=True)
        if self._consumer is not None:
            await self._consumer.stop()

    async def _consume_forever(self) -> None:
        while True:
            try:
                self._consumer = AIOKafkaConsumer(
                    *TOPICS,
                    bootstrap_servers=self.settings.kafka_bootstrap_servers,
                    group_id=self.settings.kafka_group_id,
                    enable_auto_commit=False,
                    auto_offset_reset="earliest",
                )
                await self._consumer.start()
                self.state.kafka_connected = True
                self.state.last_error = None
                async for message in self._consumer:
                    payload = event_from_bytes(message.value)
                    async with SessionLocal() as session:
                        inserted = await ingest_event(
                            session,
                            topic=message.topic,
                            payload=payload,
                            event_key=message.key.decode() if message.key else None,
                            partition=message.partition,
                            offset=message.offset,
                        )
                    await self._consumer.commit()
                    if inserted:
                        self.state.processed_events += 1
                        self.state.last_event_at = datetime.now(UTC)
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                self.state.kafka_connected = False
                self.state.last_error = str(exc)[:500]
                LOGGER.exception("Analytics Kafka consumer failed; retrying")
                if self._consumer is not None:
                    await self._consumer.stop()
                await asyncio.sleep(5)

    async def _refresh_forever(self) -> None:
        while True:
            try:
                await asyncio.sleep(self.settings.analytics_refresh_seconds)
                async with SessionLocal() as session:
                    await refresh_marts(session)
                self.state.last_refresh_at = datetime.now(UTC)
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                self.state.last_error = str(exc)[:500]
                LOGGER.exception("Analytics mart refresh failed")
