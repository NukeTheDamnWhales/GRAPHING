FROM eclipse-temurin:17-jdk-jammy
ADD target/uberjar/my-webapp-0.1.0-SNAPSHOT-standalone.jar my-webapp-0.1.0-SNAPSHOT-standalone.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "./my-webapp-0.1.0-SNAPSHOT-standalone.jar"]
