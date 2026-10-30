# syntax=docker/dockerfile:1 


#stage 1: build application 
FROM maven:3.9-eclipse-temurin-21 AS build 

WORKDIR /workspace 

COPY . .  

RUN mvn -B -ntp \
    -pl services/movie-service \
    -am \
    package \
    -DskipTests 

#stage 2: run application 
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app 

COPY --from=build \
    /workspace/services/movie-service/target/omega-movie-service.jar \
    /app/app.jar 

USER 10001: 10001 
EXPOSE 8082 
ENTRYPOINT ["java", "-Djava.awt.headless=true","-jar","/app/app.jar"] 


