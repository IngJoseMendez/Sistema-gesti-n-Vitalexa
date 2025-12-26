# Etapa 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# Copiar solo pom.xml primero (para cachear dependencias)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Compilar sin tests
RUN mvn clean package -DskipTests -B

# Etapa 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiar JAR compilado
COPY --from=build /build/target/vitalexa-backend.jar app.jar

# Variables de entorno
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport"

# Exponer puerto
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:$PORT/health || exit 1

# Comando de inicio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$PORT -Dspring.profiles.active=prod -jar app.jar"]
