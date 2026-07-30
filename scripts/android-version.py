#!/usr/bin/env python3
"""Derive Android versionName / versionCode for BigFred.

Release (CI tag push):
  GITHUB_REF_TYPE=tag and GITHUB_REF_NAME=vX.Y.Z → versionName=X.Y.Z

Otherwise (local / PR / main):
  latest git tag v* with leading v stripped, plus "-dev"
  (no tags → 0.0.0-dev)

versionCode = major*10000 + minor*100 + patch (from the SemVer core
before any '-' suffix).

Prints:
  VERSION_NAME=...
  VERSION_CODE=...
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys

SEMVER_CORE = re.compile(r"^(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$")


def strip_v(tag: str) -> str:
    tag = tag.strip()
    if tag.startswith("v") or tag.startswith("V"):
        return tag[1:]
    return tag


def semver_to_code(version_name: str) -> int:
    core = version_name.split("-", 1)[0]
    match = SEMVER_CORE.match(core)
    if not match:
        raise ValueError(f"not a SemVer versionName: {version_name!r}")
    major, minor, patch = (int(match.group(i)) for i in (1, 2, 3))
    if minor > 99 or patch > 99:
        raise ValueError(
            f"minor/patch must be <= 99 for versionCode encoding: {version_name!r}"
        )
    return major * 10000 + minor * 100 + patch


def git_latest_v_tag(repo_dir: str) -> str | None:
    try:
        out = subprocess.check_output(
            ["git", "tag", "-l", "v*", "--sort=-v:refname"],
            cwd=repo_dir,
            text=True,
            stderr=subprocess.DEVNULL,
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    for line in out.splitlines():
        tag = line.strip()
        if tag:
            return tag
    return None


def resolve_version_name(repo_dir: str) -> str:
    ref_type = os.environ.get("GITHUB_REF_TYPE", "")
    ref_name = os.environ.get("GITHUB_REF_NAME", "")
    if ref_type == "tag" and ref_name:
        name = strip_v(ref_name)
        if not SEMVER_CORE.match(name.split("-", 1)[0]):
            raise SystemExit(f"error: GitHub tag is not SemVer v*: {ref_name!r}")
        return name

    tag = git_latest_v_tag(repo_dir)
    if not tag:
        return "0.0.0-dev"
    return f"{strip_v(tag)}-dev"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo-dir",
        default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        help="git repo root (default: parent of scripts/)",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="validate only; print nothing on success",
    )
    args = parser.parse_args()

    try:
        version_name = resolve_version_name(args.repo_dir)
        version_code = max(1, semver_to_code(version_name))
    except ValueError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    if not args.check:
        print(f"VERSION_NAME={version_name}")
        print(f"VERSION_CODE={version_code}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
