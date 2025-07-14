# ------------ Build Stage ------------
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app
COPY . .
RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests

# ------------ Runtime Stage ------------
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Install tini for graceful shutdown and clean up afterwards
RUN apt-get update && apt-get install -y tini && rm -rf /var/lib/apt/lists/*

# Copy only the built jar from the build stage
COPY --from=build /app/target/DB-eventService-0.0.1-SNAPSHOT.jar ./app.jar

EXPOSE 8080

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
