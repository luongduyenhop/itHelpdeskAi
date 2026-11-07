FROM eclipse-temurin:21-jre-alpine AS builder
WORKDIR /builder
ARG JAR_FILE=target/ai-helpdesk-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=tools -jar app.jar extract --destination extracted

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /builder/extracted/lib ./lib
COPY --from=builder /builder/extracted/*.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
