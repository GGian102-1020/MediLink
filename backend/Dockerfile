FROM maven:3.9.11-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
# La CI ejecuta verify con PostgreSQL real. El build de la imagen solo compila.
RUN mvn -B -ntp -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -g 10001 app && useradd -u 10001 -g app -m app
COPY --from=builder /build/target/medlink-backend-0.0.1-SNAPSHOT.jar /app/medlink.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/medlink.jar"]
