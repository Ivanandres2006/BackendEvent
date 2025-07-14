FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy everything
COPY . .


# Install tini for better signal handling (optional but good practice)
RUN apt-get update && apt-get install -y tini

# Build the app
RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests

EXPOSE 8080

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["/bin/sh", "-c", "set -a && . .env && exec java -jar target/DB-eventService-0.0.1-SNAPSHOT.jar"]
