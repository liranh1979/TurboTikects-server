#!/usr/bin/env python3
"""
TurboTikects — Docker Build & Push (CI/CD)
Builds the obfuscated Docker image and pushes it to Docker Hub.
Must be run from the repository root (the directory that contains both
ticketmaster-client/ and TurboTikects-server/).
"""

import os
import sys
import shutil
import getpass
import subprocess
from pathlib import Path

SCRIPT_DIR   = Path(__file__).resolve().parent
DOCKERFILE   = SCRIPT_DIR / "Dockerfile"
LAST_IMAGE   = SCRIPT_DIR / ".last_image"


# ── helpers ────────────────────────────────────────────────────────────────

def confirm_repo_root():
    cwd = Path.cwd()
    if not (cwd / "ticketmaster-client").is_dir() or \
       not (cwd / "TurboTikects-server").is_dir():
        print("ERROR: Run this script from the repository root.")
        print("  Expected directories: ticketmaster-client/  TurboTikects-server/")
        sys.exit(1)

def check_docker():
    if not shutil.which("docker"):
        print("ERROR: docker not found in PATH.")
        sys.exit(1)
    result = subprocess.run(["docker", "info"], capture_output=True)
    if result.returncode != 0:
        print("ERROR: Docker daemon is not running.")
        sys.exit(1)

def prompt(msg, default=None, secret=False):
    display = f"{msg} [{default}]: " if default else f"{msg}: "
    try:
        val = getpass.getpass(display) if secret else input(display)
    except (EOFError, KeyboardInterrupt):
        print()
        sys.exit(0)
    val = val.strip()
    return val if val else default

def run(cmd, **kwargs):
    print(f"\n$ {' '.join(cmd)}")
    result = subprocess.run(cmd, **kwargs)
    if result.returncode != 0:
        print(f"\nERROR: command exited with code {result.returncode}")
        sys.exit(result.returncode)


# ── main ───────────────────────────────────────────────────────────────────

def main():
    print("=" * 60)
    print("  TurboTikects — Docker Build & Push")
    print("=" * 60)

    confirm_repo_root()
    check_docker()

    # Load previous image name as default
    default_image = "your-org/turbotikects"
    if LAST_IMAGE.exists():
        saved = LAST_IMAGE.read_text().strip().split(":")[0]
        if saved:
            default_image = saved

    print("\nDocker Hub image settings:")
    image_name = prompt("Image name (org/repo)", default_image)
    tag        = prompt("Version tag", "latest")
    full_image = f"{image_name}:{tag}"

    print(f"\nWill build and push: {full_image}")
    if tag != "latest":
        print(f"                and: {image_name}:latest")

    hub_user = prompt("\nDocker Hub username")
    hub_pass = prompt("Docker Hub password", secret=True)

    # Login
    login_cmd = ["docker", "login", "-u", hub_user, "--password-stdin"]
    proc = subprocess.Popen(login_cmd, stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    out, _ = proc.communicate(input=hub_pass.encode())
    print(out.decode().strip())
    if proc.returncode != 0:
        print("ERROR: docker login failed.")
        sys.exit(1)

    no_cache = prompt("Force --no-cache build? (y/N)", "N").lower() == "y"

    # Build
    build_cmd = ["docker", "build", "-f", str(DOCKERFILE), "-t", full_image]
    if no_cache:
        build_cmd.append("--no-cache")
    build_cmd.append(".")
    run(build_cmd)

    # Push versioned tag
    run(["docker", "push", full_image])

    # Also tag + push latest when a specific version was given
    if tag != "latest":
        latest_image = f"{image_name}:latest"
        run(["docker", "tag", full_image, latest_image])
        run(["docker", "push", latest_image])

    # Save image name for installer.py
    LAST_IMAGE.write_text(f"{image_name}:latest\n")

    print(f"\n{'=' * 60}")
    print(f"  Build complete!")
    print(f"  Image pushed: {full_image}")
    print(f"  Distribute installer.py to target hosts.")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
