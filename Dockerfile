FROM maven:3.9-eclipse-temurin-21 AS source
WORKDIR /workspace
COPY . .

FROM source AS test
CMD ["mvn", "-B", "-ntp", "-pl", "api-gateway,services/showtime-service", "-am", "test"]

FROM source AS build
ARG MODULE
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -ntp -pl "$MODULE" -am install -DskipTests \
    && mvn -B -ntp -f "$MODULE/pom.xml" package spring-boot:repackage -DskipTests \
    && cp "$MODULE"/target/*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S emovie && adduser -S emovie -G emovie
WORKDIR /app
COPY --from=build /app.jar app.jar
USER emovie
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
