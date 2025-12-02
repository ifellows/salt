#!/bin/bash

# Build production APK and copy to salt_management/data/files

set -e

echo "Building production APK..."
cd salt_android
./gradlew assembleRelease

echo "Copying APK to salt_management/data/files..."
mkdir -p ../salt_management/data/files
cp app/build/outputs/apk/release/app-release.apk ../salt_management/data/files/salt.apk

echo "Done! APK available at: salt_management/data/files/salt.apk"
