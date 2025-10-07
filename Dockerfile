FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN ./gradlew clean build -x test
CMD ["java", "-jar", "build/libs/momentum-api-0.0.1-SNAPSHOT.jar"]