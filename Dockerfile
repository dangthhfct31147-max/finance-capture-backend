FROM gradle:8.10-jdk21 AS builder

WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon -x test

FROM eclipse-temurin:21-jre AS runner

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
