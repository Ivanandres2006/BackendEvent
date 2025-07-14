FROM eclipse-temurin:17-jdk

# Use a working directory
WORKDIR /app

# Copy project files
COPY . .

# Optional: install tini for better signal handling (e.g. Docker stop)
RUN apt-get update && apt-get install -y tini

# Build app using Maven wrapper
RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests

# Expose the application port
EXPOSE 8080

# Use tini to handle signals properly
ENTRYPOINT ["/usr/bin/tini", "--"]

# Launch the application (no .env loading)
CMD ["java", "-jar", "target/DB-eventService-0.0.1-SNAPSHOT.jar"]
