#!/usr/bin/env python3
"""Fail a Play release when app/build.gradle versionCode is already used."""

import json
import os
import re
import sys
from pathlib import Path

PACKAGE = "app.bulletlauncher"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
API_ROOT = "https://androidpublisher.googleapis.com/androidpublisher/v3"
GRADLE_PATH = Path(__file__).resolve().parents[1] / "app" / "build.gradle"


def parse_version_code(gradle_text: str) -> int:
    match = re.search(r"(?m)^\s*versionCode\s+(\d+)\s*$", gradle_text)
    if not match:
        raise ValueError("versionCode not found in app/build.gradle")
    return int(match.group(1))


def max_version_code(tracks: list) -> int:
    highest = 0
    for track in tracks:
        for release in track.get("releases") or []:
            for code in release.get("versionCodes") or []:
                highest = max(highest, int(code))
    return highest


def rejection_message(local_code: int, play_code: int) -> str:
    return (
        f"Google Play already has versionCode {play_code}. "
        f"app/build.gradle has versionCode {local_code}. "
        "Raise versionCode and versionName in app/build.gradle before releasing."
    )


def ensure_unused(local_code: int, play_code: int) -> None:
    if local_code <= play_code:
        raise SystemExit(rejection_message(local_code, play_code))


def play_session(service_account_json: str):
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2 import service_account

    info = json.loads(service_account_json)
    creds = service_account.Credentials.from_service_account_info(info, scopes=[SCOPE])
    return AuthorizedSession(creds)


def _ok(response, what: str) -> None:
    try:
        response.raise_for_status()
    except Exception as exc:
        body = getattr(response, "text", "")
        raise SystemExit(f"Play API {what} failed: {exc} {str(body)[:400]}") from exc


def _delete_edit(session, package: str, edit_id: str) -> None:
    try:
        session.delete(
            f"{API_ROOT}/applications/{package}/edits/{edit_id}",
            timeout=60,
        )
    except Exception as exc:
        print(f"warning: failed to delete Play edit {edit_id}: {exc}", file=sys.stderr)


def fetch_tracks(session, package: str) -> list:
    created = session.post(
        f"{API_ROOT}/applications/{package}/edits",
        json={},
        timeout=60,
    )
    _ok(created, "create edit")
    edit_id = created.json()["id"]
    try:
        listed = session.get(
            f"{API_ROOT}/applications/{package}/edits/{edit_id}/tracks",
            timeout=60,
        )
        _ok(listed, "list tracks")
        return listed.json().get("tracks") or []
    finally:
        _delete_edit(session, package, edit_id)


def main() -> None:
    raw = os.environ.get("PLAY_STORE_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        raise SystemExit(
            "PLAY_STORE_SERVICE_ACCOUNT_JSON is missing. "
            "Refusing to publish without checking version codes already on Google Play."
        )
    local_code = parse_version_code(GRADLE_PATH.read_text())
    tracks = fetch_tracks(play_session(raw), PACKAGE)
    play_code = max_version_code(tracks)
    names = sorted(track.get("track", "") for track in tracks)
    print(
        f"Play tracks {names}: highest versionCode {play_code}; "
        f"local versionCode {local_code}"
    )
    ensure_unused(local_code, play_code)


if __name__ == "__main__":
    main()
