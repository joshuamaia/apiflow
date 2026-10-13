# syntax=docker/dockerfile:1

# ---- Build stage: Java 25 JDK + Maven wrapper ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Optional extra CA certificates (TLS-inspecting proxies/antivirus). The "certs" build context is
# provided by docker-compose (docker/certs); it is usually empty. Trusted in this stage only.
COPY --from=certs . /tmp/extra-certs/
RUN for cert in /tmp/extra-certs/*.crt; do \
      [ -f "$cert" ] || continue; \
      cp "$cert" /usr/local/share/ca-certificates/; \
      keytool -importcert -noprompt -cacerts -storepass changeit -alias "extra-$(basename "$cert")" -file "$cert" >/dev/null; \
    done; \
    update-ca-certificates >/dev/null 2>&1 || true

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/apiflowservice-*.jar extract --layers --launcher --destination target/extracted

# ---- Runtime stage: Java 25 JRE ----
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseZGC"
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q UP || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
