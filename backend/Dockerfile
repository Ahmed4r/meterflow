FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q package -DskipTests
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S meterflow && adduser -S meterflow -G meterflow
WORKDIR /app
COPY --from=build /app/target/meterflow-0.1.0.jar app.jar
USER meterflow
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
