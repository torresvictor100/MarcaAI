FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN useradd --create-home --shell /usr/sbin/nologin marcaai
WORKDIR /app
COPY --from=build /app/target/marcaai-backend.jar app.jar
USER marcaai
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
