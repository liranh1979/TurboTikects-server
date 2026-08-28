# ── Stage 1: Build (JDK 17 + Node 20) ────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS build

# Install Node.js 20 LTS
RUN apt-get update && \
    apt-get install -y ca-certificates curl gnupg && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# ── npm dependency layer (re-runs only when package.json changes) ─────────
COPY ticketmaster-client/package*.json ticketmaster-client/
RUN npm --prefix ticketmaster-client ci

# ── Gradle dependency layer (re-runs only when build.gradle changes) ──────
COPY TurboTikects-server/gradlew                          TurboTikects-server/gradlew
COPY TurboTikects-server/gradle                           TurboTikects-server/gradle
COPY TurboTikects-server/build.gradle                     TurboTikects-server/build.gradle
COPY TurboTikects-server/settings.gradle                  TurboTikects-server/settings.gradle
RUN chmod +x TurboTikects-server/gradlew
RUN cd TurboTikects-server && ./gradlew dependencies --no-daemon -q 2>/dev/null || true

# ── Full source ────────────────────────────────────────────────────────────
COPY ticketmaster-client/              ticketmaster-client/
COPY TurboTikects-server/src           TurboTikects-server/src
COPY TurboTikects-server/docker        TurboTikects-server/docker

# ── Step 1: Compile sources + npm build + ProGuard obfuscation ────────────
RUN cd TurboTikects-server && ./gradlew obfuscate -x test --no-daemon -Pobfuscate

# ── Step 2: Replace compiled classes with obfuscated ones ─────────────────
RUN rm -rf /workspace/TurboTikects-server/build/classes/java/main \
 && mkdir -p /workspace/TurboTikects-server/build/classes/java/main \
 && cd /workspace/TurboTikects-server/build/classes/java/main \
 && jar xf /workspace/TurboTikects-server/build/libs-obfuscation/app-obfuscated.jar \
 && find . -not -name "*.class" -not -type d -delete \
 && find . -empty -type d -delete

# ── Step 3: Package fat bootJar with obfuscated classes ───────────────────
RUN cd TurboTikects-server && ./gradlew bootJar -x test -x compileJava -x processResources --no-daemon

# ── Stage 2: Runtime (JRE 17 slim) ────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

ARG APP_VERSION=dev
ENV APP_VERSION=${APP_VERSION}

# FEAT-19 — AI-Generated MCP Servers spawns python3 -m venv + pip install per server at deploy
# time. Alpine's package is "python3" (no bare "python" binary), hence MCP_PYTHON_EXECUTABLE below
# rather than the Windows-dev default of "python" in application.properties. Requires PyPI egress
# from this container at deploy time — see V2/mcp-server-management/06-build-plan.html's risks.
RUN apk add --no-cache python3 py3-pip
ENV MCP_PYTHON_EXECUTABLE=python3

WORKDIR /app
RUN mkdir -p uploads mcp-scripts

COPY --from=build /workspace/TurboTikects-server/build/libs/*.jar app.jar

EXPOSE 3000
EXPOSE 3443

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
