# ----- Stg1:build -----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# ----- Copy POMs reaproveitando cache de camadas do Docker -----
COPY pom.xml .
COPY db-migrations/pom.xml db-migrations/pom.xml
COPY db-migrations/src db-migrations/src
COPY ingestor/pom.xml ingestor/pom.xml
COPY api/pom.xml api/pom.xml

# ----- Baixa dependências antes de copiar o código-fonte (cache) -----
RUN mvn -B -pl ingestor -am dependency:go-offline -q || true

# ----- Copia o código-fonte e compila -----
COPY ingestor/src ingestor/src
RUN mvn -B -pl ingestor -am package -DskipTests

# ----- Stg2: runtime -----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /app/ingestor/target/*.jar app.jar

USER app
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
