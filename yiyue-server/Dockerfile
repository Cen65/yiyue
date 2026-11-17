FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY yiyue-common ./yiyue-common
COPY yiyue-ai ./yiyue-ai
COPY yiyue-application ./yiyue-application
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S yiyue && adduser -S yiyue -G yiyue
COPY --from=build /app/yiyue-application/target/yiyue-server-0.4.0.jar app.jar
USER yiyue
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
