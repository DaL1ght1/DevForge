FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]