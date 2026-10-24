FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
RUN mvn -B -DskipTests -pl bootstrap -am package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/bootstrap/target/bootstrap-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
