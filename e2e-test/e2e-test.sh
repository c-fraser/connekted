#!/usr/bin/env sh

#Copyright 2021 c-fraser
#
#Licensed under the Apache License, Version 2.0 (the "License");
#you may not use this file except in compliance with the License.
#You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
#Unless required by applicable law or agreed to in writing, software
#distributed under the License is distributed on an "AS IS" BASIS,
#WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#See the License for the specific language governing permissions and
#limitations under the License.

set -e

SCRIPT_DIR=$(cd -- "$(dirname -- "$0")" && pwd -P)
CONNEKTED_DIR=$(dirname "$SCRIPT_DIR")

echo 'Starting minikube...'

minikube start

echo 'Building artifacts...'

# Build the CLI, images zip, and e2e application
"$CONNEKTED_DIR"/gradlew clean \
  connekted-cli:build -x test -Dquarkus.package.type=native -Dquarkus.native.container-build=true \
  createImagesZip \
  e2e-test:shadowJar

VERSION=$("$CONNEKTED_DIR"/gradlew -q printVersion)

# The path to the CLI application
CLI="$CONNEKTED_DIR"/connekted-cli/build/connekted-cli-"$VERSION"-runner

# The container images to deploy connekted with
IMAGES_ZIP="$CONNEKTED_DIR"/build/connekted-images-"$VERSION".zip

# The path to the e2e uberjar
E2E_JAR="$CONNEKTED_DIR"/e2e-test/build/libs/e2e-test-"$VERSION".jar

echo 'Deploying messaging application operator...'

# Deploy the operator and managed resources
"$CLI" deploy "$IMAGES_ZIP" -r "$DOCKER_REPOSITORY" -u "$DOCKER_USERNAME" -p "$DOCKER_PASSWORD"

# Deploy single server NATS
kubectl apply -f https://raw.githubusercontent.com/nats-io/k8s/master/nats-server/single-server-nats.yml

echo 'Running example sender...'

# Run the example sender messaging application
"$CLI" run "$E2E_JAR" -n example-sender --system-property run.example=example-02

echo 'Running example receiver...'

# Run the example receiver messaging application
"$CLI" run "$E2E_JAR" -n example-receiver --system-property run.example=example-03

echo 'Running example messaging application...'

# Run the example messaging application
"$CLI" run "$E2E_JAR" -n example-messaging-application --system-property run.example=example-05

echo 'Running Java example messaging application...'

# Run the Java example messaging application
"$CLI" run "$E2E_JAR" -n java-example-messaging-application --system-property run.example=example-06

echo 'Running example sending-receiver...'

# Run the example sending-receiver messaging application
"$CLI" run "$E2E_JAR" -n example-sending-receiver --system-property run.example=example-04

echo 'Watching logs...'

# Watch the logs for the running messaging applications for 2 minutes
"$CLI" watch \
  example-receiver \
  example-sender \
  example-messaging-application \
  java-example-messaging-application \
  example-sending-receiver \
  -s 120

echo 'Deleting deployment...'

# Stop the messaging applications and teardown the deployment
"$CLI" teardown

echo 'Deleting minikube...'

minikube delete
