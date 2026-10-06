FROM gradle:8-jdk21 AS build
COPY . /app
WORKDIR /app
RUN gradle installDist --no-daemon
FROM eclipse-temurin:21-jre
COPY --from=build /app/build/install/{{SERVICE_NAME}} /app
ENTRYPOINT ["/app/bin/{{SERVICE_NAME}}"]
