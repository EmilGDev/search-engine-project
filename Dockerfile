
FROM openjdk:17-jdk-alpine

WORKDIR /app

COPY target/SearchEngine-1.0-SNAPSHOT.jar app.jar

RUN ls -la /app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
