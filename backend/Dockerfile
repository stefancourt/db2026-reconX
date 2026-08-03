# ============================================================================
# TICKET-ADV146 — Spring Boot Dockerfile (multi-stage Maven -> JRE 25 slim)
#
# Stage 1: build the JAR with Temurin JDK 25 + Maven.
# Stage 2: copy ONLY the JAR onto a slim JRE image.
#
# Build context is the REPO ROOT, not backend/, because the api module depends
# on the sibling ADV095 starter:
#     docker build -f backend/Dockerfile -t reconx-backend .
# ============================================================================

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY backend/pom.xml backend/mvnw backend/
COPY backend/.mvn backend/.mvn
# ADV048 — copy every module POM before any source so `dependency:go-offline`
# resolves the whole reactor into a cached layer that survives source edits.
COPY backend/common/pom.xml     backend/common/
COPY backend/domain/pom.xml     backend/domain/
COPY backend/repository/pom.xml backend/repository/
COPY backend/service/pom.xml    backend/service/
COPY backend/api/pom.xml        backend/api/
COPY recon-audit-starter/pom.xml recon-audit-starter/

RUN sed -i 's/\r$//' backend/mvnw && chmod +x backend/mvnw

# ADV095 — recon-audit-starter is a sibling project, not a reactor module, and
# is published to no registry. Install it into the image's local repo first or
# the api module's dependency on it cannot resolve.
COPY recon-audit-starter/src recon-audit-starter/src
RUN ./backend/mvnw -q -B -f recon-audit-starter/pom.xml -DskipTests install

RUN ./backend/mvnw -q -B -f backend/pom.xml dependency:go-offline

COPY backend/common/src     backend/common/src
COPY backend/domain/src     backend/domain/src
COPY backend/repository/src backend/repository/src
COPY backend/service/src    backend/service/src
COPY backend/api/src        backend/api/src

# Only `api` carries the Spring Boot repackage goal, so it holds the runnable jar.
# `maven.test.skip` (not just `skipTests`) so the runtime image never waits on
# test *compilation* either — tests are CI's job (ADV154), not the image's.
RUN ./backend/mvnw -q -B -f backend/pom.xml -Dmaven.test.skip=true package && \
    mv backend/api/target/reconx-api-*.jar app.jar

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S reconx && adduser -S reconx -G reconx
USER reconx
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
