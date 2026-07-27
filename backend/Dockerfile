# ============================================================================
# TICKET-ADV146 — Spring Boot Dockerfile (multi-stage Maven -> JRE 25 slim)
#
# Stage 1: build the JAR with Temurin JDK 25 + Maven.
# Stage 2: copy ONLY the JAR onto a slim JRE image.
# ============================================================================

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
# ADV048 — copy every module POM before any source so `dependency:go-offline`
# resolves the whole reactor into a cached layer that survives source edits.
COPY common/pom.xml     common/
COPY domain/pom.xml     domain/
COPY repository/pom.xml repository/
COPY service/pom.xml    service/
COPY api/pom.xml        api/

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -q -B dependency:go-offline

COPY common/src     common/src
COPY domain/src     domain/src
COPY repository/src repository/src
COPY service/src    service/src
COPY api/src        api/src

# Only `api` carries the Spring Boot repackage goal, so it holds the runnable jar.
RUN ./mvnw -q -B -DskipTests package && \
    mv api/target/reconx-api-*.jar app.jar

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S reconx && adduser -S reconx -G reconx
USER reconx
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
