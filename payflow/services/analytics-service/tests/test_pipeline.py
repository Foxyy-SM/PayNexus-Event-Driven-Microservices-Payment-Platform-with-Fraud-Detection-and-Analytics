from app.config import Settings
from app.pipeline import AnalyticsPipeline, PipelineState


def test_pipeline_starts_disconnected_and_reports_no_error():
    pipeline = AnalyticsPipeline(Settings(kafka_enabled=False))
    assert pipeline.state == PipelineState()
    assert pipeline.settings.kafka_enabled is False
