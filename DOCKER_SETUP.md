# 🚀 Guía de Inicio Rápido - Docker Compose

Esta guía te permitirá iniciar el proyecto completo en cualquier máquina nueva en minutos.

## 📋 Prerrequisitos

- Docker instalado ([Descargar Docker Desktop](https://www.docker.com/products/docker-desktop))
- Docker Compose (incluido con Docker Desktop)

## 🏃 Inicio Rápido

### 1️⃣ Clonar el repositorio (si aún no lo has hecho)
```bash
git clone <url-del-repo>
cd Sistema-gesti-n-Vitalexa
```

### 2️⃣ Levantar los servicios
```bash
docker-compose up -d
```

Este comando hará automáticamente:
- ✅ Descarga la imagen de PostgreSQL 15
- ✅ Crea la base de datos `inventory_db`
- ✅ Construye tu aplicación backend desde el Dockerfile
- ✅ Ejecuta las migraciones de Flyway automáticamente
- ✅ Levanta ambos servicios conectados

### 3️⃣ Verificar que todo está corriendo
```bash
docker-compose ps
```

Deberías ver:
```
NAME                  STATUS          PORTS
vitalexa-backend      Up             0.0.0.0:8080->8080/tcp
vitalexa-postgres     Up (healthy)   0.0.0.0:5432->5432/tcp
```

### 4️⃣ Ver los logs (opcional)
```bash
# Ver logs de todos los servicios
docker-compose logs -f

# Ver logs solo del backend
docker-compose logs -f backend

# Ver logs solo de la base de datos
docker-compose logs -f postgres
```

### 5️⃣ Probar la API
```bash
curl http://localhost:8080/api/auth/health
```

## 🛑 Detener los servicios

### Detener sin eliminar los contenedores
```bash
docker-compose stop
```

### Detener y eliminar contenedores (pero conservar datos)
```bash
docker-compose down
```

### Detener, eliminar contenedores Y eliminar la base de datos
```bash
docker-compose down -v
```
⚠️ **CUIDADO**: Esto eliminará todos los datos de la base de datos.

## 🔄 Reiniciar desde cero

Si necesitas reiniciar completamente (por ejemplo, después de cambios en el esquema):

```bash
# 1. Detener y eliminar todo (incluyendo datos)
docker-compose down -v

# 2. Eliminar la imagen del backend para reconstruir
docker rmi vitalexa-backend

# 3. Levantar de nuevo
docker-compose up -d --build
```

## 🔧 Configuración avanzada

### Variables de entorno personalizadas

1. Copia el archivo de ejemplo:
```bash
cp .env.example .env
```

2. Edita `.env` con tus valores personalizados

3. Docker Compose usará automáticamente el archivo `.env`

### Conectarse a la base de datos directamente

**Desde la máquina host:**
```bash
psql -h localhost -p 5432 -U postgres -d inventory_db
# Password: postgres
```

**Desde el contenedor:**
```bash
docker-compose exec postgres psql -U postgres -d inventory_db
```

### Ejecutar comandos Maven dentro del contenedor

```bash
# Abrir una shell en el contenedor del backend
docker-compose exec backend sh

# Dentro del contenedor puedes ejecutar comandos
```

## ❓ Troubleshooting

### El puerto 5432 ya está en uso
Si ya tienes PostgreSQL instalado localmente:
- Opción 1: Detén el PostgreSQL local
- Opción 2: Cambia el puerto en `docker-compose.yml`:
  ```yaml
  ports:
    - "5433:5432"  # Usa 5433 en lugar de 5432
  ```

### El puerto 8080 ya está en uso
Cambia el puerto en `docker-compose.yml`:
```yaml
ports:
  - "8081:8080"  # Usa 8081 externamente
```

### La aplicación no se conecta a la base de datos
1. Verifica que PostgreSQL está healthy:
   ```bash
   docker-compose ps
   ```
2. Revisa los logs:
   ```bash
   docker-compose logs postgres
   docker-compose logs backend
   ```

### Necesito reconstruir la aplicación
```bash
docker-compose up -d --build backend
```

## 📊 Datos de prueba

Si necesitas datos de prueba iniciales, ejecuta:
```bash
docker-compose exec postgres psql -U postgres -d inventory_db -f /path/to/seed.sql
```

## 🔑 Credenciales por defecto

**Base de datos:**
- Host: localhost
- Puerto: 5432
- Database: inventory_db
- Usuario: postgres
- Contraseña: postgres

**API:**
- URL: http://localhost:8080
- Ver documentación de endpoints en `DOCUMENTACION_API_COMPLETA.md`
