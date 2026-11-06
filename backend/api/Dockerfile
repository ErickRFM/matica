FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew && ./gradlew -Pmatica.backendOnly=true :backend:api:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /opt/matica
COPY --from=builder /workspace/backend/api/build/install/api/ ./
ENV PORT=10000
EXPOSE 10000
CMD ["./bin/api"]
