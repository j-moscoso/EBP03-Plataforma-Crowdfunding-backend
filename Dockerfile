# --- Etapa 1: build con Maven + JDK 21 ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copiamos primero el wrapper y el pom para aprovechar la cache de capas de Docker
COPY plataforma_crowdfunding_backend/.mvn/ .mvn/
COPY plataforma_crowdfunding_backend/mvnw plataforma_crowdfunding_backend/mvnw.cmd ./
COPY plataforma_crowdfunding_backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Ahora copiamos el resto del código fuente y compilamos
COPY plataforma_crowdfunding_backend/src ./src
RUN ./mvnw clean package -DskipTests -B

# --- Etapa 2: runtime liviano, solo el JRE ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Copiamos el jar ya compilado desde la etapa de build
COPY --from=build /app/target/*.jar app.jar

# Render inyecta la variable PORT; Spring Boot debe escuchar en ella
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT}"]