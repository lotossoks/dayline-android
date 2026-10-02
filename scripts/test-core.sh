#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/core-tests build/test-reports
javac --release 17 -encoding UTF-8 -d build/core-tests app/src/main/java/app/dayline/core/*.java tests/CoreTest.java tests/AnalysisTest.java
java -cp build/core-tests CoreTest build/test-reports
java -cp build/core-tests AnalysisTest
python3 tests/verify_excel.py build/test-reports
