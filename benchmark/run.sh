#!/usr/bin/env bash
# Canterbury benchmark runner.
# downloads the corpus on first run, then compiles and runs Benchmark.java.
set -e

cd "$(dirname "$0")/.."  #run from repo root regardless of where script is called from

CORPUS_DIR="benchmark/corpus"
BIN_MAIN="bin/main"
BIN_BENCH="bin/benchmark"
CORPUS_URL="https://corpus.canterbury.ac.nz/resources/cantrbry.zip"

#download corpus if the directory is missing or empty
if [ ! -d "$CORPUS_DIR" ] || [ -z "$(ls -A "$CORPUS_DIR" 2>/dev/null)" ]; then
    echo "downloading Canterbury corpus from $CORPUS_URL ..."
    mkdir -p "$CORPUS_DIR"
    TMP_ZIP="$CORPUS_DIR/cantrbry.zip"

    if command -v curl &>/dev/null; then
        curl -L --fail "$CORPUS_URL" -o "$TMP_ZIP"
    elif command -v wget &>/dev/null; then
        wget -O "$TMP_ZIP" "$CORPUS_URL"
    else
        echo "error: neither curl nor wget is available."
        echo "manually download cantrbry.zip from $CORPUS_URL and unzip it into $CORPUS_DIR/"
        exit 1
    fi

    unzip -j -o "$TMP_ZIP" -d "$CORPUS_DIR"  #-j flattens any subdirectory in the zip
    rm "$TMP_ZIP"
    echo "corpus ready in $CORPUS_DIR/"
fi

#compile main library sources
echo "compiling main sources..."
mkdir -p "$BIN_MAIN"
javac -d "$BIN_MAIN" @sources-main.txt

#compile benchmark
echo "compiling benchmark..."
mkdir -p "$BIN_BENCH"
javac -cp "$BIN_MAIN" -d "$BIN_BENCH" benchmark/Benchmark.java

#run
echo ""
java -cp "$BIN_MAIN:$BIN_BENCH" Benchmark "$CORPUS_DIR"
