# ms-chatbot — imagen de ejecución (docker-compose.yml en la raíz).
# Las pruebas no corren aquí (usan Testcontainers): se ejecutan fuera con ./mvnw test.

# ---------- Compilación ----------
FROM eclipse-temurin:17-jdk AS compilacion
WORKDIR /fuente

# Capa de dependencias: solo se vuelve a descargar si cambia pom.xml o el wrapper.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

# ---------- Ejecución ----------
FROM eclipse-temurin:17-jre
RUN groupadd --system samaria && useradd --system --gid samaria --no-create-home samaria
WORKDIR /app
COPY --from=compilacion /fuente/app.jar app.jar
USER samaria
EXPOSE 8085
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
