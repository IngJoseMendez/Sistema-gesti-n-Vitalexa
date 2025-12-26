# Etapa 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# Copiar solo pom.xml primero (para cachear dependencias)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Compilar sin tests CON encoding UTF-8 explícito
RUN mvn clean package -DskipTests -B -Dproject.build.sourceEncoding=UTF-8 -Dproject.reporting.outputEncoding=UTF-8

# DEBUGGING: Listar archivos generados
RUN ls -la /build/target/

# Etapa 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiar JAR compilado (ahora con el path correcto)
COPY --from=build /build/target/vitalexa-backend.jar app.jar

# Variables de entorno
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -Dfile.encoding=UTF-8"

# Exponer puerto
EXPOSE 8080

# Comando de inicio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$PORT -Dspring.profiles.active=prod -jar app.jar"]
