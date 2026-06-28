# ── Preserve annotations (Spring, JPA, Jackson all need them) ──────────
-keepattributes *Annotation*, Signature, EnclosingMethod, InnerClasses, MethodParameters

# ── Spring component scanning needs exact class names ──────────────────
-keep @org.springframework.stereotype.Component class * { *; }
-keep @org.springframework.stereotype.Service class * { *; }
-keep @org.springframework.stereotype.Repository class * { *; }
-keep @org.springframework.web.bind.annotation.RestController class * { *; }
-keep @org.springframework.web.bind.annotation.Controller class * { *; }
-keep @org.springframework.context.annotation.Configuration class * { *; }
-keep @org.springframework.boot.autoconfigure.SpringBootApplication class * { *; }
-keep @org.springframework.scheduling.annotation.EnableScheduling class * { *; }

-keepclassmembers @org.springframework.context.annotation.Configuration class * {
    @org.springframework.context.annotation.Bean <methods>;
}

# ── REST endpoint method signatures ────────────────────────────────────
-keepclassmembers @org.springframework.web.bind.annotation.RestController class * {
    public *;
}

# ── JPA entities: keep class + all members (JPQL uses class names) ─────
-keep @jakarta.persistence.Entity class * { *; }
-keep @jakarta.persistence.MappedSuperclass class * { *; }
-keep @jakarta.persistence.Table class * { *; }
-keep @jakarta.persistence.Embeddable class * { *; }

# ── @IdClass POJOs: not annotated with @Entity but must match entity field names
-keep class com.turbotikects.turbotikectsserver.entitys.** { *; }

# ── Spring Data repositories ────────────────────────────────────────────
-keep interface com.turbotikects.turbotikectsserver.repositorys.** { *; }
-keep interface * extends org.springframework.data.repository.Repository { *; }

# ── DTOs: Jackson serialization needs field and method names ────────────
-keep class com.turbotikects.turbotikectsserver.dto.** { *; }

# ── LLM provider response-parsing helper classes (e.g. GemmaLlmProvider's
# OllamaModel, GeminiLlmProvider's GeminiResponse/Candidate/Content): these live
# next to each LlmProvider implementation rather than in dto/, so the rule above
# doesn't cover them. Jackson binds them by getter/setter name via reflection —
# without this they get silently mis-parsed (fields stay null, no exception) once
# obfuscated. Every such helper class in this codebase is tagged @JsonIgnoreProperties,
# so keying off that annotation covers all of them, present and future.
-keep @com.fasterxml.jackson.annotation.JsonIgnoreProperties class * { *; }

# ── ApplicationRunner / CommandLineRunner implementations ───────────────
-keep class * implements org.springframework.boot.ApplicationRunner { *; }
-keep class * implements org.springframework.boot.CommandLineRunner { *; }

# ── Scheduled tasks (method names referenced by reflection) ────────────
-keepclassmembers class * {
    @org.springframework.scheduling.annotation.Scheduled <methods>;
}

# ── Main entry point ────────────────────────────────────────────────────
-keep class com.turbotikects.turbotikectsserver.TurboTikectsServerApplication {
    public static void main(java.lang.String[]);
}

# ── Serializable (Infinispan session caching) ───────────────────────────
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

# ── Bouncy Castle: SSL/TLS certificate parsing (PEM → PKCS12 conversion) ──
-keep class org.bouncycastle.** { *; }

# ── Suppress unavoidable warnings from Spring/library internal classes ──
-dontwarn **
-ignorewarnings
