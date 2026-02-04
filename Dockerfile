# Etapa 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests -B \
    -Dproject.build.sourceEncoding=UTF-8 \
    -Dproject.reporting.outputEncoding=UTF-8

# Etapa 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copiar JAR y renombrarlo a app.jar
COPY --from=build /build/target/*.jar app.jar

# Variables de entorno
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -Dfile.encoding=UTF-8"

EXPOSE 8080

# Usar app.jar (NO target/vitalexa-backend.jar)
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$PORT -Dspring.profiles.active=prod -jar app.jar"]
