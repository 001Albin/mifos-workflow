# ---- Build Stage ----
FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /app

# 1. Copy parent POM AND all submodule descriptors to leverage Docker layer caching
COPY pom.xml .
COPY mifos-workflow-core/pom.xml ./mifos-workflow-core/
COPY mifos-workflow-adapter-flowable/pom.xml ./mifos-workflow-adapter-flowable/
COPY mifos-workflow-adapter-conductor/pom.xml ./mifos-workflow-adapter-conductor/
COPY mifos-workflow-app/pom.xml ./mifos-workflow-app/

# Download all dependencies globally across the reactor tree
RUN mvn dependency:go-offlin

# Copy source code
COPY src ./src
COPY mifos-workflow-core/src ./mifos-workflow-core/src
COPY mifos-workflow-adapter-flowable/src ./mifos-workflow-adapter-flowable/src
COPY mifos-workflow-adapter-conductor/src ./mifos-workflow-adapter-conductor/src
COPY mifos-workflow-app/src ./mifos-workflow-app/src
    
# Package the application
RUN mvn clean package -DskipTests

# Build all modules, explicitly skipping the repackage goal since submodules are empty right now
RUN mvn clean package -DskipTests -Dspring-boot.repackage.skip=true

# ---- Run Stage ----
FROM eclipse-temurin:21.0.10_7-jre
WORKDIR /app
    
# 3. Copy the artifact explicitly from the mifos-workflow-app target directory
COPY --from=build /app/mifos-workflow-app/target/*.jar app.jar
    
# Expose application port
EXPOSE 8081
    
# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]