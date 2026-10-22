# Imagen común para los microservicios del backend. Contexto de build: backend/
#   docker build --build-arg SERVICE=order-service -t cloudorders/order-service backend
ARG SERVICE

FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY . .
RUN mvn -B -q -pl ${SERVICE} -am -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
ARG SERVICE
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 appuser
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-*.jar app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
