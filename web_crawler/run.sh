#!/bin/bash

echo "compiling..."
javac -cp "lib/*" -d out src/*.java || exit 1

echo "running..."
java -cp "out:lib/*" Main 