#!/bin/bash

# Script para compilar Vitalexa con Java 17
# Esto asegura que siempre se use la versión correcta de Java

export JAVA_HOME=~/.jdks/jdk-17.0.13+11/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

echo "✅ Usando Java 17 para compilar"
java -version

echo ""
echo "🔨 Compilando proyecto..."
echo ""

./mvnw clean package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ ¡Compilación exitosa!"
    echo "📦 JAR creado: target/vitalexa-backend.jar"
    echo ""
    echo "Para ejecutar, usa: ./run-with-java17.sh"
else
    echo ""
    echo "❌ Error en la compilación"
    exit 1
fi
