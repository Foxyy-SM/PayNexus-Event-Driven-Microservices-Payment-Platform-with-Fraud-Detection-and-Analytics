from dataclasses import dataclass
from functools import lru_cache

import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jwt import PyJWKClient

from .config import Settings, get_settings

bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class Principal:
    subject: str
    email: str | None
    roles: frozenset[str]


@lru_cache(maxsize=4)
def jwk_client(url: str) -> PyJWKClient:
    return PyJWKClient(url)


def decode_principal(token: str, settings: Settings) -> Principal:
    signing_key = jwk_client(settings.keycloak_jwk_set_uri).get_signing_key_from_jwt(token)
    claims = jwt.decode(
        token,
        signing_key.key,
        algorithms=["RS256"],
        issuer=settings.keycloak_issuer_uri,
        options={"require": ["exp", "iat", "sub", "iss"]},
    )
    roles = claims.get("realm_access", {}).get("roles", [])
    return Principal(
        subject=claims["sub"],
        email=claims.get("email") or claims.get("preferred_username"),
        roles=frozenset(str(role).upper() for role in roles),
    )


def require_admin(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    settings: Settings = Depends(get_settings),
) -> Principal:
    if settings.auth_disabled:
        return Principal("local-test", "test@paynexus.local", frozenset({"ADMIN"}))
    if credentials is None:
        raise HTTPException(status_code=401, detail="Bearer token required")
    try:
        principal = decode_principal(credentials.credentials, settings)
    except Exception as exc:
        raise HTTPException(status_code=401, detail="Invalid bearer token") from exc
    if "ADMIN" not in principal.roles:
        raise HTTPException(status_code=403, detail="ADMIN role required")
    return principal
