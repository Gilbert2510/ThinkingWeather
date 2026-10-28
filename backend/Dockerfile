# Build first in the project root: ./gradlew :backend:installDist
# Then: docker build -t thinking-weather-gateway backend
FROM eclipse-temurin:25-jre
WORKDIR /opt/weather-gateway
COPY --chown=10001:10001 build/install/backend/ ./
USER 10001:10001
ENV BIND_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["/opt/weather-gateway/bin/backend"]
