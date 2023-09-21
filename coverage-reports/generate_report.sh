#!/bin/bash

# Specify the path to the Maven project directory
project_directory="../sdk"
SNAPSHOT_VERSION_TAG="0.8.0-SNAPSHOT"

# Step 1: Change to the project directory
echo "Changing to the project directory..."
cd "$project_directory" || exit 1

# Step 2: Run 'mvn clean install' in the project directory
echo "Running 'mvn clean install' in the project directory..."
mvn clean install

# Step 3: Execute run_sc_tests.sh
echo "Executing script for integration tests..."
/bash ../qa/run_sc_tests.sh

# Step 4: Generate the JaCoCo code coverage report
echo "Generating JaCoCo code coverage report..."
java -jar /home/david/.m2/repository/org/jacoco/org.jacoco.cli/0.8.9/org.jacoco.cli-0.8.9-nodeps.jar \
  report /home/david/Desktop/Sidechains-SDK/coverage-reports/sidechains-sdk-${SNAPSHOT_VERSION_TAG}/sidechains-sdk-${SNAPSHOT_VERSION_TAG}-jacoco-report.exec \
  --classfiles /home/david/Desktop/Sidechains-SDK/sdk/target/classes \
  --html /home/david/Desktop/Sidechains-SDK/coverage-reports/sidechains-sdk-${SNAPSHOT_VERSION_TAG}/sidechains-sdk-${SNAPSHOT_VERSION_TAG}-jacoco-report

echo "Code coverage report generation complete."
