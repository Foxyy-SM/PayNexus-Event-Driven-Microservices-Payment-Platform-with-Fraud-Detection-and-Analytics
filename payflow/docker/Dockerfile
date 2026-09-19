FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
ARG SERVICE
COPY pom.xml .
COPY libs libs
COPY services services
RUN apk add --no-cache maven
RUN mvn -q -pl services/${SERVICE} -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG SERVICE
COPY --from=build /workspace/services/${SERVICE}/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseG1GC","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
