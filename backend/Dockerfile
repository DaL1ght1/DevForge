FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /build

COPY pom.xml mvnw ./
COPY .mvn .mvn

RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
COPY templates templates
COPY workspace workspace

RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S devforge && adduser -S -G devforge devforge

COPY --from=build /build/target/*.jar /app/app.jar
COPY --from=build /build/templates /app/templates
COPY --from=build /build/workspace /app/workspace

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    DEVFORGE_TEMPLATES_ROOT=/app/templates

USER devforge

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
