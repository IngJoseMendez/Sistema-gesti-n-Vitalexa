#!/bin/bash
# Script de ayuda para deployment optimizado

echo "🚀 Script de Deployment - Sistema Gestión Vitalexa"
echo "=================================================="

# 1. Verificar cambios pendientes
if [[ -n $(git status -s) ]]; then
    echo "⚠️  Tienes cambios sin commitear:"
    git status -s
    echo ""
    read -p "¿Deseas hacer commit ahora? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git add .
        read -p "Mensaje de commit: " commit_msg
        git commit -m "$commit_msg"
    fi
fi

# 2. Push a producción
echo ""
echo "📤 Haciendo push a producción..."
git push

echo ""
echo "✅ Push completado"
echo ""
echo "📋 Próximos pasos:"
echo "  1. Ve a tu plataforma de deployment (Railway/Render/etc)"
echo "  2. Si el build falla con exit code 143:"
echo "     - Limpia el cache de Docker"
echo "     - Fuerza un rebuild sin cache"
echo "  3. Verifica los logs de deployment"
echo ""
echo "💡 Si el problema persiste:"
echo "  - El servidor puede tener menos de 2GB RAM"
echo "  - Considera hacer build local y deploy del JAR"
echo "  - Comando local: ./mvnw clean package -DskipTests"
