FROM maven:3.9-eclipse-temurin-17 AS dependencies
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline

FROM dependencies AS test
COPY src ./src
CMD ["mvn", "-B", "test"]

FROM dependencies AS build
COPY src ./src
RUN mvn -B clean package -DskipTests


FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
