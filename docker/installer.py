#!/usr/bin/env python3
"""
TurboTikects — Installer / Upgrade Wizard
Supports Windows and Linux (GUI via tkinter; falls back to CLI if unavailable).

Usage:
    python installer.py          # GUI wizard (default)
    python installer.py --cli    # Force CLI mode
"""

import os
import sys
import shutil
import socket
import getpass
import subprocess
import threading
import time
import webbrowser
import re
import urllib.request
from pathlib import Path

SCRIPT_DIR  = Path(__file__).resolve().parent
COMPOSE_TPL = SCRIPT_DIR / "docker-compose.yml"
LAST_IMAGE  = SCRIPT_DIR / ".last_image"
COMPOSE_OUT = Path.cwd() / "docker-compose.yml"

DEFAULT_IMAGE = "liran1979/turbotikects:latest"

# ── Colour palette ─────────────────────────────────────────────────────────
BG       = "#0f172a"
BG2      = "#1e293b"
ACCENT   = "#6366f1"
ACCENT2  = "#818cf8"
FG       = "#e2e8f0"
FG_DIM   = "#94a3b8"
GREEN    = "#10b981"
RED      = "#ef4444"
YELLOW   = "#f59e0b"
BTN_FG   = "#ffffff"


# ══════════════════════════════════════════════════════════════════════════════
#  Shared logic (used by both GUI and CLI)
# ══════════════════════════════════════════════════════════════════════════════

def get_compose_cmd():
    """Return compose command list: ['docker','compose'] or ['docker-compose']."""
    try:
        subprocess.run(["docker", "compose", "version"],
                       check=True, capture_output=True)
        return ["docker", "compose"]
    except Exception:
        if shutil.which("docker-compose"):
            return ["docker-compose"]
    return None

def docker_available():
    return bool(shutil.which("docker"))

def docker_running():
    try:
        r = subprocess.run(["docker", "info"], capture_output=True, timeout=10)
        return r.returncode == 0
    except Exception:
        return False

def port_in_use(port):
    try:
        with socket.create_connection(("localhost", int(port)), timeout=1):
            return True
    except Exception:
        return False

def load_default_image():
    if LAST_IMAGE.exists():
        v = LAST_IMAGE.read_text().strip()
        if v:
            return v
    if COMPOSE_OUT.exists():
        txt = COMPOSE_OUT.read_text()
        m = re.search(r'image:\s*["\']?([^\s"\']+)["\']?', txt)
        if m and "mysql" not in m.group(1):
            return m.group(1)
    return DEFAULT_IMAGE

def parse_existing_compose():
    """Return dict of settings from an existing docker-compose.yml, or None."""
    if not COMPOSE_OUT.exists():
        return None
    txt = COMPOSE_OUT.read_text()
    cfg = {}
    for key, pattern in [
        ("docker_image", r'image:\s*["\']?([^\s"\']+)["\']?'),
        ("db_name",      r'MYSQL_DATABASE:\s*["\']?([^\s"\']+)["\']?'),
        ("db_pass",      r'MYSQL_ROOT_PASSWORD:\s*["\']?([^\s"\']+)["\']?'),
        ("app_port",     r'"(\d+):3000"'),
    ]:
        m = re.search(pattern, txt)
        if m:
            val = m.group(1)
            if key == "docker_image" and "mysql" in val:
                continue
            cfg[key] = val
    return cfg if cfg else None

def generate_compose(cfg):
    """Substitute placeholders in the template and write docker-compose.yml."""
    if COMPOSE_TPL.exists():
        tpl = COMPOSE_TPL.read_text()
    else:
        tpl = EMBEDDED_TEMPLATE
    result = tpl
    for k, v in cfg.items():
        # Escape $ in values so Docker Compose doesn't treat them as variables
        safe_v = str(v).replace("$", "$$")
        result = result.replace(f"${{{k.upper()}}}", safe_v)
    COMPOSE_OUT.write_text(result)

def poll_ready(port, timeout=120):
    url = f"http://localhost:{port}/api/v1/locales/en"
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            urllib.request.urlopen(url, timeout=3)
            return True
        except Exception:
            time.sleep(3)
    return False

# Embedded template (fallback if docker-compose.yml not alongside installer)
EMBEDDED_TEMPLATE = """services:
  db:
    image: mysql:8.0
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: "${DB_PASS}"
      MYSQL_DATABASE: "${DB_NAME}"
    volumes:
      - db_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-p${DB_PASS}"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 30s

  app:
    image: "${DOCKER_IMAGE}"
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    ports:
      - "${APP_PORT}:3000"
    environment:
      DB_HOST: db
      DB_NAME: "${DB_NAME}"
      DB_USER: root
      DB_PASS: "${DB_PASS}"
      ADMIN_USERNAME: "${ADMIN_USERNAME}"
      ADMIN_PASSWORD: "${ADMIN_PASSWORD}"
      ADMIN_DISPLAY_NAME: "${ADMIN_DISPLAY_NAME}"
      CORS_ORIGINS: "http://localhost:${APP_PORT}"
    volumes:
      - uploads:/app/uploads

volumes:
  db_data:
  uploads:
"""


# ══════════════════════════════════════════════════════════════════════════════
#  GUI Wizard
# ══════════════════════════════════════════════════════════════════════════════

def run_gui():
    import tkinter as tk
    from tkinter import ttk, scrolledtext, messagebox

    WIN_W, WIN_H = 560, 480

    class App(tk.Tk):
        def __init__(self):
            super().__init__()
            self.title("TurboTikects Installer")
            self.geometry(f"{WIN_W}x{WIN_H}")
            self.resizable(False, False)
            self.configure(bg=BG)
            self._center()

            self.cfg       = {}
            self.mode      = "install"   # "install" | "upgrade"
            self.compose   = get_compose_cmd()
            self._frame    = None

            self._setup_styles()
            self.show_preflight()

        def _center(self):
            self.update_idletasks()
            x = (self.winfo_screenwidth()  - WIN_W) // 2
            y = (self.winfo_screenheight() - WIN_H) // 2
            self.geometry(f"{WIN_W}x{WIN_H}+{x}+{y}")

        def _setup_styles(self):
            s = ttk.Style(self)
            s.theme_use("default")
            s.configure("TFrame",         background=BG)
            s.configure("Card.TFrame",    background=BG2)
            s.configure("TLabel",         background=BG,  foreground=FG,
                        font=("Segoe UI", 10))
            s.configure("Title.TLabel",   background=BG,  foreground=FG,
                        font=("Segoe UI", 16, "bold"))
            s.configure("Sub.TLabel",     background=BG,  foreground=FG_DIM,
                        font=("Segoe UI", 9))
            s.configure("Card.TLabel",    background=BG2, foreground=FG,
                        font=("Segoe UI", 10))
            s.configure("Step.TLabel",    background=BG,  foreground=FG_DIM,
                        font=("Segoe UI", 8))
            s.configure("OK.TLabel",      background=BG,  foreground=GREEN,
                        font=("Segoe UI", 10))
            s.configure("Err.TLabel",     background=BG,  foreground=RED,
                        font=("Segoe UI", 10))
            s.configure("TEntry",         fieldbackground=BG2, foreground=FG,
                        insertcolor=FG, font=("Segoe UI", 10))
            s.configure("Primary.TButton", background=ACCENT, foreground=BTN_FG,
                        font=("Segoe UI", 10, "bold"), padding=(14, 7))
            s.map("Primary.TButton",
                  background=[("active", ACCENT2)])
            s.configure("TButton",        background=BG2,  foreground=FG,
                        font=("Segoe UI", 10), padding=(10, 6))
            s.map("TButton",
                  background=[("active", "#334155")])
            s.configure("TCheckbutton",   background=BG, foreground=FG,
                        font=("Segoe UI", 10))

        # ── frame management ────────────────────────────────────────────────

        def _clear(self):
            if self._frame:
                self._frame.destroy()
            self._frame = ttk.Frame(self, style="TFrame")
            self._frame.place(x=0, y=0, width=WIN_W, height=WIN_H)
            return self._frame

        def _header(self, parent, title, subtitle=None, step=None):
            ttk.Label(parent, text="🎫  TurboTikects", style="Sub.TLabel"
                      ).pack(anchor="w", padx=30, pady=(24, 0))
            ttk.Label(parent, text=title, style="Title.TLabel"
                      ).pack(anchor="w", padx=30, pady=(4, 0))
            if subtitle:
                ttk.Label(parent, text=subtitle, style="Sub.TLabel"
                          ).pack(anchor="w", padx=30, pady=(2, 0))
            if step:
                ttk.Label(parent, text=step, style="Step.TLabel"
                          ).pack(anchor="e", padx=30, pady=(0, 4))
            ttk.Separator(parent, orient="horizontal").pack(fill="x",
                          padx=30, pady=(6, 0))

        def _nav(self, parent, back=None, next_cmd=None,
                 next_txt="Next  →", next_style="Primary.TButton"):
            bar = ttk.Frame(parent, style="TFrame")
            bar.pack(side="bottom", fill="x", padx=30, pady=16)
            if back:
                ttk.Button(bar, text="← Back", command=back
                           ).pack(side="left")
            if next_cmd:
                ttk.Button(bar, text=next_txt, style=next_style,
                           command=next_cmd).pack(side="right")

        def _field(self, parent, label, var, show=None, width=36):
            row = ttk.Frame(parent, style="TFrame")
            row.pack(fill="x", padx=30, pady=4)
            ttk.Label(row, text=label, style="TLabel", width=20
                      ).pack(side="left")
            kw = {"textvariable": var, "width": width}
            if show:
                kw["show"] = show
            e = ttk.Entry(row, **kw)
            e.pack(side="left", fill="x", expand=True)
            return e

        def _inline_status(self, parent):
            lbl = ttk.Label(parent, text="", style="TLabel")
            lbl.pack(anchor="w", padx=30)
            return lbl

        # ── screens ─────────────────────────────────────────────────────────

        def show_preflight(self):
            f = self._clear()
            self._header(f, "Pre-flight Checks",
                         "Verifying your environment before we begin…")

            card = ttk.Frame(f, style="Card.TFrame", relief="flat", padding=16)
            card.pack(fill="both", padx=30, pady=16, expand=True)

            checks = [
                ("Python ≥ 3.8",          lambda: sys.version_info >= (3, 8)),
                ("Docker installed",       docker_available),
                ("Docker daemon running",  docker_running),
                ("Docker Compose found",   lambda: bool(get_compose_cmd())),
            ]

            labels  = []
            icons   = []
            results = []

            for name, _ in checks:
                row = ttk.Frame(card, style="Card.TFrame")
                row.pack(fill="x", pady=4)
                ic = ttk.Label(row, text="…", style="Card.TLabel", width=3)
                ic.pack(side="left")
                ttk.Label(row, text=name, style="Card.TLabel"
                          ).pack(side="left", padx=8)
                icons.append(ic)
                labels.append(name)

            btn_frame = ttk.Frame(f, style="TFrame")
            btn_frame.pack(side="bottom", fill="x", padx=30, pady=16)
            cont_btn = ttk.Button(btn_frame, text="Continue  →",
                                  style="Primary.TButton",
                                  command=self.show_welcome, state="disabled")
            cont_btn.pack(side="right")
            retry_btn = ttk.Button(btn_frame, text="Retry",
                                   command=self.show_preflight)
            retry_btn.pack(side="right", padx=(0, 8))
            err_lbl = ttk.Label(btn_frame, text="", style="Err.TLabel")
            err_lbl.pack(side="left")

            def run_checks():
                all_ok = True
                for i, (_, fn) in enumerate(checks):
                    ok = False
                    try:
                        ok = fn()
                    except Exception:
                        pass
                    icon_txt = "✓" if ok else "✗"
                    icon_style = "OK.TLabel" if ok else "Err.TLabel"
                    # Patch style on Card background
                    icons[i].config(text=icon_txt, foreground=GREEN if ok else RED)
                    results.append(ok)
                    if not ok:
                        all_ok = False
                        msgs = {
                            0: "Python 3.8+ is required.",
                            1: "Docker not found. Install from https://docs.docker.com/get-docker/",
                            2: "Docker is not running. Start Docker Desktop or the Docker service.",
                            3: "Docker Compose not found. Included with Docker Desktop.",
                        }
                        err_lbl.config(text=msgs.get(i, "Check failed."))
                        break
                    time.sleep(0.15)
                if all_ok:
                    self.compose = get_compose_cmd()
                    cont_btn.config(state="normal")
                    err_lbl.config(text="All checks passed.", foreground=GREEN)

            threading.Thread(target=run_checks, daemon=True).start()

        def show_welcome(self):
            existing = parse_existing_compose()
            f = self._clear()
            self._header(f, "Welcome", "What would you like to do?")

            card = ttk.Frame(f, style="Card.TFrame", relief="flat", padding=24)
            card.pack(fill="both", padx=30, pady=20, expand=True)

            def do_install():
                self.mode = "install"
                self.show_image_step()

            def do_upgrade():
                self.mode = "upgrade"
                cfg = parse_existing_compose() or {}
                self.cfg.update(cfg)
                self.show_image_step()

            ttk.Button(card, text="🚀  Fresh Install",
                       style="Primary.TButton", width=32,
                       command=do_install).pack(pady=8)
            ttk.Label(card, text="Set up TurboTikects for the first time.",
                      style="Card.TLabel", foreground=FG_DIM
                      ).pack(pady=(0, 16))

            upg_btn = ttk.Button(card, text="🔄  Upgrade Existing",
                                 width=32, command=do_upgrade)
            upg_btn.pack(pady=8)
            if not existing:
                upg_btn.config(state="disabled")
                note = "No docker-compose.yml found in current directory."
            else:
                note = f"Detected: {existing.get('docker_image', 'unknown image')}"
            ttk.Label(card, text=note, style="Card.TLabel",
                      foreground=FG_DIM).pack(pady=(0, 8))

        def show_image_step(self):
            f = self._clear()
            step_lbl = "Step 1 of 4" if self.mode == "install" else "Step 1 of 3"
            self._header(f, "Docker Image", "Which image should be deployed?",
                         step=step_lbl)

            img_var = tk.StringVar(value=self.cfg.get("docker_image",
                                                       load_default_image()))
            self._field(f, "Image name", img_var)

            status_lbl = self._inline_status(f)

            def check_image():
                img = img_var.get().strip()
                status_lbl.config(text="Checking…", foreground=YELLOW)
                f.update_idletasks()
                r = subprocess.run(["docker", "manifest", "inspect", img],
                                   capture_output=True)
                if r.returncode == 0:
                    status_lbl.config(text=f"✓  {img} is available.",
                                      foreground=GREEN)
                else:
                    status_lbl.config(text=f"✗  Image not found or not accessible.",
                                      foreground=RED)

            ttk.Button(f, text="Check availability", command=check_image
                       ).pack(anchor="w", padx=30, pady=(4, 0))

            def go_next():
                img = img_var.get().strip()
                if not img:
                    status_lbl.config(text="Image name is required.", foreground=RED)
                    return
                self.cfg["docker_image"] = img
                if self.mode == "install":
                    self.show_database_step()
                else:
                    self.show_network_step()

            self._nav(f, back=self.show_welcome, next_cmd=go_next)

        def show_database_step(self):
            f = self._clear()
            self._header(f, "Database", "Configure the MySQL database.",
                         step="Step 2 of 4")

            db_var  = tk.StringVar(value=self.cfg.get("db_name",  "turbotikects"))
            pw_var  = tk.StringVar(value=self.cfg.get("db_pass",  ""))
            pw2_var = tk.StringVar(value="")
            self._field(f, "Database name", db_var)
            self._field(f, "MySQL password", pw_var, show="●")
            self._field(f, "Confirm password", pw2_var, show="●")

            err_lbl = self._inline_status(f)

            def go_next():
                db   = db_var.get().strip()
                pw   = pw_var.get()
                pw2  = pw2_var.get()
                if not db:
                    err_lbl.config(text="Database name is required.", foreground=RED); return
                if len(pw) < 8:
                    err_lbl.config(text="Password must be at least 8 characters.", foreground=RED); return
                if pw != pw2:
                    err_lbl.config(text="Passwords do not match.", foreground=RED); return
                self.cfg.update({"db_name": db, "db_pass": pw})
                self.show_admin_step()

            self._nav(f, back=self.show_image_step, next_cmd=go_next)

        def show_admin_step(self):
            f = self._clear()
            self._header(f, "Admin Account",
                         "Create the super-admin user.", step="Step 3 of 4")

            user_var = tk.StringVar(value=self.cfg.get("admin_username", "admin"))
            pw_var   = tk.StringVar(value=self.cfg.get("admin_password", ""))
            pw2_var  = tk.StringVar(value="")
            dn_var   = tk.StringVar(value=self.cfg.get("admin_display_name", "Administrator"))

            self._field(f, "Username", user_var)
            self._field(f, "Password", pw_var, show="●")
            self._field(f, "Confirm password", pw2_var, show="●")
            self._field(f, "Display name", dn_var)

            # Auto-fill display name from username
            def on_user(*_):
                if not dn_var.get() or dn_var.get() == self._prev_user.capitalize():
                    dn_var.set(user_var.get().capitalize())
                self._prev_user = user_var.get()
            self._prev_user = user_var.get()
            user_var.trace_add("write", on_user)

            err_lbl = self._inline_status(f)

            def go_next():
                u   = user_var.get().strip()
                pw  = pw_var.get()
                pw2 = pw2_var.get()
                dn  = dn_var.get().strip()
                if not u:
                    err_lbl.config(text="Username is required.", foreground=RED); return
                if len(pw) < 6:
                    err_lbl.config(text="Password must be at least 6 characters.", foreground=RED); return
                if pw != pw2:
                    err_lbl.config(text="Passwords do not match.", foreground=RED); return
                self.cfg.update({"admin_username": u, "admin_password": pw,
                                 "admin_display_name": dn or u})
                self.show_network_step()

            self._nav(f, back=self.show_database_step, next_cmd=go_next)

        def show_network_step(self):
            step = "Step 4 of 4" if self.mode == "install" else "Step 2 of 3"
            f = self._clear()
            self._header(f, "Network", "Configure the host port.", step=step)

            port_var   = tk.StringVar(value=str(self.cfg.get("app_port", "3000")))
            browser_var = tk.BooleanVar(value=True)
            self._field(f, "Host port", port_var, width=10)
            ttk.Checkbutton(f, text="Open browser automatically after deploy",
                            variable=browser_var, style="TCheckbutton"
                            ).pack(anchor="w", padx=30, pady=8)

            err_lbl = self._inline_status(f)

            def go_next():
                try:
                    p = int(port_var.get())
                    assert 1 <= p <= 65535
                except Exception:
                    err_lbl.config(text="Enter a valid port (1–65535).", foreground=RED); return
                if self.mode == "install" and port_in_use(p):
                    err_lbl.config(text=f"Port {p} is already in use.", foreground=RED); return
                self.cfg["app_port"] = p
                self.cfg["open_browser"] = browser_var.get()
                self.show_summary()

            back = self.show_admin_step if self.mode == "install" else self.show_image_step
            self._nav(f, back=back, next_cmd=go_next)

        def show_summary(self):
            step = "Step 5 of 5" if self.mode == "install" else "Step 3 of 3"
            f = self._clear()
            action = "Install" if self.mode == "install" else "Upgrade"
            self._header(f, "Summary", "Review your settings before deploying.",
                         step=step)

            card = ttk.Frame(f, style="Card.TFrame", relief="flat", padding=16)
            card.pack(fill="both", padx=30, pady=12, expand=True)

            rows = [
                ("Image",          self.cfg.get("docker_image", "")),
                ("Database",       self.cfg.get("db_name", "")),
                ("DB Password",    "●" * min(len(str(self.cfg.get("db_pass", ""))), 10)),
                ("App Port",       str(self.cfg.get("app_port", "3000"))),
            ]
            if self.mode == "install":
                rows += [
                    ("Admin User",     self.cfg.get("admin_username", "")),
                    ("Admin Password", "●" * min(len(str(self.cfg.get("admin_password", ""))), 10)),
                    ("Display Name",   self.cfg.get("admin_display_name", "")),
                ]
            for lbl, val in rows:
                row = ttk.Frame(card, style="Card.TFrame")
                row.pack(fill="x", pady=2)
                ttk.Label(row, text=lbl + ":", style="Card.TLabel",
                          width=16, foreground=FG_DIM).pack(side="left")
                ttk.Label(row, text=val, style="Card.TLabel"
                          ).pack(side="left", padx=8)

            back = self.show_network_step
            self._nav(f, back=back,
                      next_cmd=self.start_deploy,
                      next_txt=f"{action}  ▶",
                      next_style="Primary.TButton")

        def start_deploy(self):
            f = self._clear()
            action = "Installing" if self.mode == "install" else "Upgrading"
            self._header(f, f"{action}…", "Please wait while Docker sets everything up.")

            # Progress bar
            pb = ttk.Progressbar(f, mode="indeterminate", length=500)
            pb.pack(padx=30, pady=(16, 8))
            pb.start(12)

            # Log output
            log = scrolledtext.ScrolledText(
                f, bg="#0a0f1a", fg=FG, font=("Courier New", 9),
                insertbackground=FG, relief="flat", height=12, wrap="word"
            )
            log.pack(fill="both", padx=30, pady=(0, 8), expand=True)
            log.config(state="disabled")

            status_lbl = ttk.Label(f, text="Starting…", style="Sub.TLabel")
            status_lbl.pack(anchor="w", padx=30)

            def append_log(line):
                log.config(state="normal")
                log.insert("end", line + "\n")
                log.see("end")
                log.config(state="disabled")

            def set_status(msg, color=FG_DIM):
                status_lbl.config(text=msg, foreground=color)

            def run_deploy():
                cfg = self.cfg
                try:
                    # Generate compose file
                    self.after(0, lambda: set_status("Generating docker-compose.yml…"))
                    generate_compose({
                        "DOCKER_IMAGE": cfg.get("docker_image", ""),
                        "DB_NAME":      cfg.get("db_name", "turbotikects"),
                        "DB_PASS":      cfg.get("db_pass", ""),
                        "APP_PORT":     str(cfg.get("app_port", 3000)),
                        "ADMIN_USERNAME":    cfg.get("admin_username", ""),
                        "ADMIN_PASSWORD":    cfg.get("admin_password", ""),
                        "ADMIN_DISPLAY_NAME": cfg.get("admin_display_name", ""),
                    })
                    self.after(0, lambda: append_log("[✓] docker-compose.yml written"))

                    compose = self.compose

                    # Pull image
                    self.after(0, lambda: set_status("Pulling image from Docker Hub…"))
                    self.after(0, lambda: append_log(f"[…] docker pull {cfg['docker_image']}"))
                    r = subprocess.run(
                        ["docker", "pull", cfg["docker_image"]],
                        capture_output=True, text=True
                    )
                    for line in r.stdout.splitlines():
                        self.after(0, lambda l=line: append_log(l))
                    if r.returncode != 0:
                        raise RuntimeError(r.stderr or "docker pull failed")
                    self.after(0, lambda: append_log("[✓] Image pulled"))

                    # Start / update stack
                    if self.mode == "upgrade":
                        self.after(0, lambda: set_status("Recreating app container…"))
                        cmd = compose + ["up", "-d", "--force-recreate", "app"]
                    else:
                        self.after(0, lambda: set_status("Starting stack (MySQL + app)…"))
                        cmd = compose + ["up", "-d"]

                    proc = subprocess.Popen(
                        cmd,
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                        text=True,
                        creationflags=(subprocess.CREATE_NO_WINDOW
                                       if sys.platform == "win32" else 0)
                    )
                    for line in proc.stdout:
                        line = line.rstrip()
                        self.after(0, lambda l=line: append_log(l))
                    proc.wait()
                    if proc.returncode != 0:
                        raise RuntimeError("docker compose up failed")
                    self.after(0, lambda: append_log("[✓] Containers started"))

                    # Wait for app to be ready
                    port = cfg.get("app_port", 3000)
                    self.after(0, lambda: set_status("Waiting for app to be ready…"))
                    self.after(0, lambda: append_log(
                        f"[…] Polling http://localhost:{port}/api/v1/locales/en"))
                    ready = poll_ready(port, timeout=120)
                    if not ready:
                        raise RuntimeError("App did not become ready in time. Check logs.")
                    self.after(0, lambda: append_log("[✓] App is ready!"))
                    self.after(0, lambda: self.show_success(port))

                except Exception as exc:
                    self.after(0, lambda e=str(exc): self.show_error_screen(e, log))
                finally:
                    self.after(0, pb.stop)

            threading.Thread(target=run_deploy, daemon=True).start()

        def show_success(self, port):
            f = self._clear()
            url = f"http://localhost:{port}"

            ttk.Label(f, text="", style="TLabel").pack(pady=20)
            ttk.Label(f, text="✓", font=("Segoe UI", 48), background=BG,
                      foreground=GREEN).pack()
            ttk.Label(f, text="Deployment Complete!", style="Title.TLabel"
                      ).pack(pady=(8, 4))
            ttk.Label(f, text=f"TurboTikects is running at  {url}",
                      style="Sub.TLabel").pack()

            if self.mode == "install":
                u = self.cfg.get("admin_username", "admin")
                ttk.Label(f, text=f"Login with your admin credentials  ({u})",
                          style="Sub.TLabel").pack(pady=(4, 0))

            ttk.Label(f, text="", style="TLabel").pack(pady=8)

            def open_browser():
                webbrowser.open(url)

            ttk.Button(f, text="🌐  Open in Browser", style="Primary.TButton",
                       command=open_browser).pack(pady=4)
            ttk.Button(f, text="Exit", command=self.destroy).pack(pady=4)

            if self.cfg.get("open_browser", True):
                self.after(1000, open_browser)

        def show_error_screen(self, msg, log_widget=None):
            if log_widget:
                log_widget.config(state="normal")
                log_widget.insert("end", f"\n[✗] ERROR: {msg}\n")
                log_widget.config(state="disabled")

            # Show error panel at bottom
            err_frame = ttk.Frame(self._frame, style="TFrame")
            err_frame.pack(side="bottom", fill="x", padx=30, pady=12)
            ttk.Label(err_frame, text=f"✗  {msg}", foreground=RED,
                      background=BG, font=("Segoe UI", 9),
                      wraplength=400).pack(side="left")
            ttk.Button(err_frame, text="Exit",
                       command=self.destroy).pack(side="right")

    app = App()
    app.mainloop()


# ══════════════════════════════════════════════════════════════════════════════
#  CLI fallback
# ══════════════════════════════════════════════════════════════════════════════

def run_cli():
    print("=" * 60)
    print("  TurboTikects — Installer (CLI mode)")
    print("=" * 60)

    # Pre-flight
    print("\nRunning pre-flight checks…")
    checks = [
        ("Python ≥ 3.8",         sys.version_info >= (3, 8)),
        ("Docker installed",      docker_available()),
        ("Docker daemon running", docker_running()),
        ("Docker Compose found",  bool(get_compose_cmd())),
    ]
    for name, ok in checks:
        status = "OK" if ok else "FAIL"
        print(f"  [{status:4s}] {name}")
        if not ok:
            print("\nFix the above issue and re-run the installer.")
            sys.exit(1)

    compose = get_compose_cmd()

    # Mode
    existing = parse_existing_compose()
    if existing:
        print(f"\nExisting deployment detected: {existing.get('docker_image', '?')}")
        mode_in = input("Upgrade (u) or fresh install (i)? [u]: ").strip().lower()
        mode = "install" if mode_in == "i" else "upgrade"
    else:
        mode = "install"
        print(f"\nMode: fresh install")

    def prompt(msg, default=None, secret=False):
        display = f"{msg} [{default}]: " if default else f"{msg}: "
        try:
            val = getpass.getpass(display) if secret else input(display)
        except (EOFError, KeyboardInterrupt):
            print(); sys.exit(0)
        val = val.strip()
        return val if val else default

    cfg = dict(existing or {})
    cfg["docker_image"] = prompt("Docker image", cfg.get("docker_image", load_default_image()))

    if mode == "install":
        cfg["db_name"] = prompt("Database name", cfg.get("db_name", "turbotikects"))
        while True:
            pw  = prompt("MySQL root password", secret=True)
            pw2 = prompt("Confirm password",    secret=True)
            if pw and pw == pw2 and len(pw) >= 8:
                cfg["db_pass"] = pw; break
            print("Passwords must match and be at least 8 characters.")
        cfg["admin_username"]    = prompt("Admin username", "admin")
        while True:
            pw  = prompt("Admin password", secret=True)
            pw2 = prompt("Confirm password", secret=True)
            if pw and pw == pw2 and len(pw) >= 6:
                cfg["admin_password"] = pw; break
            print("Passwords must match and be at least 6 characters.")
        cfg["admin_display_name"] = prompt(
            "Admin display name", cfg["admin_username"].capitalize())

    cfg["app_port"] = int(prompt("Host port", str(cfg.get("app_port", "3000"))))

    print("\nGenerating docker-compose.yml…")
    generate_compose({
        "DOCKER_IMAGE": cfg.get("docker_image", ""),
        "DB_NAME":      cfg.get("db_name", "turbotikects"),
        "DB_PASS":      cfg.get("db_pass", ""),
        "APP_PORT":     str(cfg.get("app_port", 3000)),
        "ADMIN_USERNAME":     cfg.get("admin_username", ""),
        "ADMIN_PASSWORD":     cfg.get("admin_password", ""),
        "ADMIN_DISPLAY_NAME": cfg.get("admin_display_name", ""),
    })
    print("  Written: docker-compose.yml")

    print(f"\nPulling image: {cfg['docker_image']}")
    subprocess.run(["docker", "pull", cfg["docker_image"]], check=True)

    if mode == "upgrade":
        cmd = compose + ["up", "-d", "--force-recreate", "app"]
    else:
        cmd = compose + ["up", "-d"]
    print(f"\nStarting: {' '.join(cmd)}")
    subprocess.run(cmd, check=True)

    port = cfg.get("app_port", 3000)
    print(f"\nWaiting for app on port {port}…")
    if poll_ready(port):
        print(f"\n✓ TurboTikects is running at http://localhost:{port}")
        if mode == "install":
            print(f"  Login: {cfg.get('admin_username', 'admin')} / (your password)")
    else:
        print("\nApp did not respond in time — check logs with: docker compose logs app")


# ══════════════════════════════════════════════════════════════════════════════
#  Entry point
# ══════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    force_cli = "--cli" in sys.argv

    if not force_cli:
        try:
            import tkinter  # noqa: F401
            run_gui()
        except ImportError:
            print("tkinter not available — falling back to CLI mode.")
            print("(Install with: sudo apt install python3-tk  or  brew install python-tk)\n")
            run_cli()
    else:
        run_cli()
