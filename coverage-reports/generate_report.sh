#!/bin/bash

# Specify snapshot version
SNAPSHOT_VERSION_TAG="0.8.0-SNAPSHOT"

# Check if SIDECHAIN_SDK is set and not empty
if [ -z "$SIDECHAIN_SDK" ]; then
  echo "Error: SIDECHAIN_SDK is not set or is empty. Please set the environment variable."
  exit 1
fi

# Step 1: Change to the project directory
echo "Changing to the project directory..."
cd ../sdk || exit 1

# Step 2: Run 'mvn clean install' in the project directory
echo "Running 'mvn clean install' in the project directory..."
mvn clean install

# Check if BITCOIND and BITCOINCLI are set and not empty before running python tests
if [ -z "$BITCOIND" ] || [ -z "$BITCOINCLI" ]; then
  echo "Error: Either BITCOIND or BITCOINCLI is not set or is empty. Please set both environment variables."
  exit 1
fi

# Step 3: Execute run_sc_tests.sh
echo "Executing script for integration tests..."
cd ../qa || exit 1
./run_sc_tests.sh -jacoco

# Step 4: Generate the JaCoCo code coverage report
echo "Generating JaCoCo code coverage report..."

JACOCO_JAR_PATH="$HOME/.m2/repository/org/jacoco/org.jacoco.cli/0.8.9/org.jacoco.cli-0.8.9-nodeps.jar"
EXEC_PATH="$SIDECHAIN_SDK/coverage-reports/sidechains-sdk-${SNAPSHOT_VERSION_TAG}/sidechains-sdk-${SNAPSHOT_VERSION_TAG}-jacoco-report.exec"
CLASSFILES_PATH="$SIDECHAIN_SDK/sdk/target/classes"
HTML_PATH="$SIDECHAIN_SDK/coverage-reports/sidechains-sdk-${SNAPSHOT_VERSION_TAG}/sidechains-sdk-${SNAPSHOT_VERSION_TAG}-jacoco-report"

java -jar "$JACOCO_JAR_PATH" \
  report "$EXEC_PATH" \
  --classfiles "$CLASSFILES_PATH" \
  --html "$HTML_PATH"

echo "Code coverage report generation complete."
