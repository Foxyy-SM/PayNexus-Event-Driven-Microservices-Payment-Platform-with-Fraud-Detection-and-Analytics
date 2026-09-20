from contextlib import asynccontextmanager

from fastapi import FastAPI

from .api import router
from .config import get_settings
from .database import initialize_database
from .pipeline import AnalyticsPipeline

settings = get_settings()
pipeline = AnalyticsPipeline(settings)


@asynccontextmanager
async def lifespan(app: FastAPI):
    await initialize_database()
    app.state.settings = settings
    app.state.pipeline = pipeline
    await pipeline.start()
    try:
        yield
    finally:
        await pipeline.stop()


app = FastAPI(
    title="PayNexus Analytics API",
    version="1.0.0",
    description="Read-only payment analytics, risk intelligence, and ledger data-quality API.",
    lifespan=lifespan,
)
app.include_router(router)
