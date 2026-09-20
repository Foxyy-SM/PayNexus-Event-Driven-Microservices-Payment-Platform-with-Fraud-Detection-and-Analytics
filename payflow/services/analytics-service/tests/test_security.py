from unittest.mock import patch

import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials

from app.config import Settings
from app.security import Principal, require_admin


def test_admin_role_is_required():
    credentials = HTTPAuthorizationCredentials(scheme="Bearer", credentials="token")
    with (
        patch(
            "app.security.decode_principal",
            return_value=Principal("user", "user@test.local", frozenset({"USER"})),
        ),
        pytest.raises(HTTPException) as error,
    ):
        require_admin(credentials, Settings())
    assert error.value.status_code == 403


def test_auth_can_be_disabled_only_by_explicit_setting():
    principal = require_admin(None, Settings(auth_disabled=True))
    assert "ADMIN" in principal.roles


def test_missing_token_is_unauthorized():
    with pytest.raises(HTTPException) as error:
        require_admin(None, Settings())
    assert error.value.status_code == 401


def test_admin_token_is_accepted():
    credentials = HTTPAuthorizationCredentials(scheme="Bearer", credentials="token")
    expected = Principal("admin", "admin@test.local", frozenset({"ADMIN"}))
    with patch("app.security.decode_principal", return_value=expected):
        assert require_admin(credentials, Settings()) == expected
