from functools import lru_cache
from urllib.parse import quote_plus

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "PayNexus Analytics"
    environment: str = "local"
    database_url: str | None = None
    db_host: str = "localhost"
    db_port: int = 5432
    db_user: str = "paynexus"
    db_password: str = "paynexus"
    db_name: str = "paynexus_analytics"
    kafka_brokers: str = "localhost:9092"
    kafka_group_id: str = "paynexus-analytics-v1"
    kafka_enabled: bool = True
    analytics_refresh_seconds: int = 30
    inbox_directory: str = "inbox"
    keycloak_issuer_uri: str = "http://localhost:8088/realms/paynexus"
    keycloak_jwk_set_uri: str = "http://localhost:8088/realms/paynexus/protocol/openid-connect/certs"
    auth_disabled: bool = False

    @model_validator(mode="after")
    def build_database_url(self) -> "Settings":
        if not self.database_url:
            self.database_url = (
                f"postgresql+asyncpg://{quote_plus(self.db_user)}:{quote_plus(self.db_password)}"
                f"@{self.db_host}:{self.db_port}/{self.db_name}"
            )
        return self

    @property
    def kafka_bootstrap_servers(self) -> list[str]:
        return [server.strip() for server in self.kafka_brokers.split(",") if server.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
