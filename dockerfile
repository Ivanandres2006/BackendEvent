FROM eclipse-temurin:17-jdk

WORKDIR /app

# Only copy what's needed
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src

# Build the app
RUN ./mvnw clean package -DskipTests

EXPOSE 8080

CMD ["java", "-jar", "target/DB-eventService-0.0.1-SNAPSHOT.jar"]
