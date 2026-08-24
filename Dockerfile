# Builds the whole application - API and UI - into a single image.
#
# The UI is bundled into the same jar as the API on purpose. The browser then loads the pages
# and calls /api from one origin, which is exactly what frontend/js/api.js already assumes
# anywhere other than localhost (it uses a relative "/api"). Serving the two as separate
# services would break that path, drag CORS back in, and cost twice as much for no benefit at
# this size. Local development still runs them apart, and api.js handles that case explicitly.
#
# Build context is the repository root, so both backend/ and frontend/ are reachable.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies first, so a source-only change does not re-resolve them.
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline

COPY backend/src ./src

# Only the assets the browser needs - see .dockerignore for what is kept out.
COPY frontend/*.html ./src/main/resources/static/
COPY frontend/*.css  ./src/main/resources/static/
COPY frontend/js     ./src/main/resources/static/js
COPY frontend/css    ./src/main/resources/static/css

RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

# Runs as a non-root user: nothing here needs write access to the image, and a container
# process that cannot write is one less thing to worry about.
RUN useradd --system --uid 10001 --create-home appuser
USER appuser

COPY --from=build --chown=appuser:appuser /build/target/*.jar app.jar

EXPOSE 8080

# Heap is capped deliberately: the platform bills for memory, and this application idles at
# well under 256 MB. SerialGC because a single small container gains nothing from a
# concurrent collector and pays for it in memory.
ENTRYPOINT ["java", "-Xmx256m", "-Xms128m", "-XX:MaxMetaspaceSize=128m", "-Xss512k", "-XX:+UseSerialGC", "-jar", "app.jar"]
