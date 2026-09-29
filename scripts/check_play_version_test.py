import os
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_play_version


ROOT = Path(__file__).resolve().parents[1]


class ParseVersionCodeTest(unittest.TestCase):
    def test_reads_version_code_line(self):
        text = 'defaultConfig {\n        versionCode 115\n        versionName "v6.7.24"\n}\n'
        self.assertEqual(check_play_version.parse_version_code(text), 115)

    def test_rejects_missing_version_code(self):
        with self.assertRaises(ValueError):
            check_play_version.parse_version_code('versionName "v6.7.24"\n')

    def test_repo_gradle_has_a_version_code(self):
        text = (ROOT / "app" / "build.gradle").read_text()
        self.assertGreaterEqual(check_play_version.parse_version_code(text), 115)


class MaxVersionCodeTest(unittest.TestCase):
    def test_highest_code_across_tracks(self):
        tracks = [
            {"track": "internal", "releases": [{"versionCodes": ["114", "112"]}]},
            {"track": "production", "releases": [{"versionCodes": [90]}]},
            {"track": "beta"},
        ]
        self.assertEqual(check_play_version.max_version_code(tracks), 114)

    def test_empty_tracks(self):
        self.assertEqual(check_play_version.max_version_code([]), 0)


class EnsureUnusedTest(unittest.TestCase):
    def test_equal_code_is_rejected(self):
        with self.assertRaises(SystemExit) as ctx:
            check_play_version.ensure_unused(114, 114)
        self.assertIn("already has versionCode 114", str(ctx.exception))
        self.assertIn("has versionCode 114", str(ctx.exception))

    def test_older_code_is_rejected(self):
        with self.assertRaises(SystemExit):
            check_play_version.ensure_unused(113, 114)

    def test_newer_code_passes(self):
        check_play_version.ensure_unused(115, 114)


class FakeResponse:
    def __init__(self, payload=None, status=200):
        self.payload = payload or {}
        self.status_code = status
        self.text = "nope"

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"HTTP {self.status_code}")

    def json(self):
        return self.payload


class FakeSession:
    def __init__(self, fail_get=False):
        self.fail_get = fail_get
        self.deleted = []

    def post(self, url, json=None, timeout=None):
        return FakeResponse({"id": "edit-1"})

    def get(self, url, timeout=None):
        if self.fail_get:
            return FakeResponse(status=500)
        return FakeResponse(
            {
                "tracks": [
                    {
                        "track": "internal",
                        "releases": [{"versionCodes": ["114"]}],
                    }
                ]
            }
        )

    def delete(self, url, timeout=None):
        self.deleted.append(url)
        return FakeResponse({})


class FetchTracksTest(unittest.TestCase):
    def test_returns_tracks_and_deletes_edit(self):
        session = FakeSession()
        tracks = check_play_version.fetch_tracks(session, "app.bulletlauncher")
        self.assertEqual(tracks[0]["track"], "internal")
        self.assertEqual(len(session.deleted), 1)
        self.assertIn("/edits/edit-1", session.deleted[0])

    def test_deletes_edit_when_list_fails(self):
        session = FakeSession(fail_get=True)
        with self.assertRaises(SystemExit):
            check_play_version.fetch_tracks(session, "app.bulletlauncher")
        self.assertEqual(len(session.deleted), 1)


class MainTest(unittest.TestCase):
    def test_refuses_without_service_account(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(SystemExit) as ctx:
                check_play_version.main()
        self.assertIn("PLAY_STORE_SERVICE_ACCOUNT_JSON", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
