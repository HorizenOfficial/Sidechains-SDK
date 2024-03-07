#!/bin/bash

# this script generates jacoco code coverage report
# before running it, it is necessary to have the following env vars set - $SIDECHAIN_SDK, $BITCOIND and $BITCOINCLI
# additionally, it is necessary to have org.jacoco.cli-0.8.9-nodeps.jar which can be found here - https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/0.8.9/
# this script should be run in the root of coverage-reports folder

# Specify snapshot version
SNAPSHOT_VERSION_TAG="0.12.0"

# Check if SIDECHAIN_SDK is set and not empty
if [ -z "$SIDECHAIN_SDK" ]; then
  echo "Error: SIDECHAIN_SDK is not set or is empty. Please set the environment variable."
  exit 1
fi

# Step 1: Change to the project directory
echo "Changing to the project directory..."
cd ../sdk || exit 1

# Step 2: Run 'mvn clean install' in the project directory
# it is necessary to let test phase run as well, because this phase creates .exec file for this code coverage report
echo "Running 'mvn clean install' in the project directory..."
mvn clean install

# Check if BITCOIND and BITCOINCLI are set and not empty before running python tests
if [ -n "$BITCOIND" ] && [ -n "$BITCOINCLI" ]; then
  # Step 3: Execute run_sc_tests.sh
  # this phase runs python tests which append to the previously created .exec file to get the full code coverage report
  echo "Executing script for integration tests..."
  cd ../qa || exit 1
  ./run_sc_tests.sh -jacoco
else
    echo "Warning: Either BITCOIND or BITCOINCLI is not set or is empty. Please set both environment variables."
fi

# Step 3: Execute run_sc_tests.sh
# this phase runs python tests which append to the previously created .exec file to get the full code coverage report
echo "Executing script for integration tests..."
cd ../qa || exit 1
./run_sc_tests.sh -jacoco

# Step 4: Generate the JaCoCo code coverage report
# this phase creates the detailed report with html files for easier browsing
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
