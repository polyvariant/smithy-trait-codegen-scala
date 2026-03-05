#!/usr/bin/env bash
set -e
set -x

temp=$(mktemp -d)

cs launch --contrib smithy-cli -- build --output "$temp"

cd "$temp/source/trait-codegen"

echo "Entered temp dir: $PWD"

CP="$(cs fetch --classpath software.amazon.smithy:smithy-model:1.68.0)"

files="$(find . -name "*.java" | tr '\n' ' ')"

javac -cp "$CP" $files
javadoc -cp "$CP" $files
