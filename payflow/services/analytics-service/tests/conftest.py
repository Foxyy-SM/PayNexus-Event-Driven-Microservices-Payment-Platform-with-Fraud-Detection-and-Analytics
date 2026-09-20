from types import SimpleNamespace

import pytest
import pytest_asyncio
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from app.api import router
from app.database import session_dependency
from app.models import Base
from app.security import Principal, require_admin


@pytest_asyncio.fixture
async def session():
    engine = create_async_engine("sqlite+aiosqlite:///:memory:")
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    maker = async_sessionmaker(engine, expire_on_commit=False)
    async with maker() as value:
        yield value
    await engine.dispose()


@pytest.fixture
def test_app(session, tmp_path):
    app = FastAPI()
    app.include_router(router)
    app.state.settings = SimpleNamespace(inbox_directory=str(tmp_path))
    app.state.pipeline = SimpleNamespace(
        state=SimpleNamespace(
            kafka_connected=True,
            processed_events=2,
            last_event_at=None,
            last_refresh_at=None,
            last_error=None,
        )
    )

    async def override_session():
        yield session

    app.dependency_overrides[session_dependency] = override_session
    app.dependency_overrides[require_admin] = lambda: Principal(
        "test", "admin@test.local", frozenset({"ADMIN"})
    )
    return app


@pytest_asyncio.fixture
async def client(test_app):
    async with AsyncClient(transport=ASGITransport(app=test_app), base_url="http://test") as value:
        yield value
