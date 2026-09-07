# ==============================
# Build stage
# ==============================
FROM gradle:8.14-jdk21 AS builder

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew clean bootJar -x test


# ==============================
# Runtime stage
# ==============================
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]