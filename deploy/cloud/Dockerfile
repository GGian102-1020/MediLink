FROM maven:3.9.11-eclipse-temurin-21 AS builder
WORKDIR /build
COPY backend/pom.xml .
COPY backend/src ./src
# Cloud-only additions leave the working local backend sources untouched.
COPY deploy/cloud/backend/ ./src/
RUN mvn -B -ntp -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -g 10001 app && useradd -u 10001 -g app -m app
COPY --from=builder /build/target/medlink-backend-0.0.1-SNAPSHOT.jar /app/medifind.jar
ENV SPRING_PROFILES_ACTIVE=cloud
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:+ExitOnOutOfMemoryError -Xss512k"
USER app
EXPOSE 10000
ENTRYPOINT ["java","-jar","/app/medifind.jar"]
