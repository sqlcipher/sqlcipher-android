#!/usr/bin/env bash

set -e -x

ROOT_DIR="$(realpath "$PWD")"
WORK_DIR="$ROOT_DIR/external"

EXTERNAL_DEPENDENCIES_ROOT_DIR="$ROOT_DIR/sqlcipher/src/main/jni/sqlcipher"

# remove existing working directory
rm -rf "$WORK_DIR"
rm -rf "$EXTERNAL_DEPENDENCIES_ROOT_DIR/android-libs"
rm -rf "$EXTERNAL_DEPENDENCIES_ROOT_DIR/sqlite.c" "$EXTERNAL_DEPENDENCIES_ROOT_DIR/sqlite.h"

# initialize working directory
mkdir -p "$WORK_DIR"
pushd "$WORK_DIR"

# download Android NDK LTS version
# see https://developer.android.com/ndk/downloads/#lts-downloads
wget  "https://dl.google.com/android/repository/android-ndk-r23c-linux.zip"
unzip "android-ndk-r23c-linux.zip"

export ANDROID_API=23
export ANDROID_NDK_HOME="$WORK_DIR/android-ndk-r23c"

# download OpenSSL 1.1.1 LTS version
wget "https://www.openssl.org/source/openssl-1.1.1o.tar.gz"
tar xvzf "openssl-1.1.1o.tar.gz"

export OPENSSL_HOME="$WORK_DIR/openssl-1.1.1o"

function build_openssl () {
  # variables
  ARCH="$1"
  OUTPUT_DIR="$EXTERNAL_DEPENDENCIES_ROOT_DIR/android-libs/$2"

  # see https://developer.android.com/ndk/guides/other_build_systems
  export TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"

  export CC=clang
  export CXX=clang++
  export AR=llvm-ar
  export RANLIB=llvm-ranlib
  export STRIP=llvm-strip

  export PATH="$TOOLCHAIN/bin:$PATH"

  pushd "$OPENSSL_HOME"

  if [ -f "Makefile" ]; then
    make clean
  fi

  ./Configure "$ARCH" -D__ANDROID_API_="$ANDROID_API"
  make build_generated && make libcrypto.a

  mkdir -p "$OUTPUT_DIR"
  cp -p "$OPENSSL_HOME/libcrypto.a" "$OUTPUT_DIR/"
  popd
}

mkdir -p "$EXTERNAL_DEPENDENCIES_ROOT_DIR/android-libs"
cp -rp "$OPENSSL_HOME/include" "$EXTERNAL_DEPENDENCIES_ROOT_DIR/android-libs/include"

build_openssl "android-x86" "x86"
build_openssl "android-x86_64" "x86_64"
build_openssl "android-arm" "armeabi-v7a"
build_openssl "android-arm64" "arm64-v8a"

# download sqlite.c and sqlite.h
wget "https://sqlite.org/2022/sqlite-amalgamation-3380500.zip"
unzip "sqlite-amalgamation-3380500.zip"

cp -p "sqlite-amalgamation-3380500/sqlite3.c" "$EXTERNAL_DEPENDENCIES_ROOT_DIR/"
cp -p "sqlite-amalgamation-3380500/sqlite3.h" "$EXTERNAL_DEPENDENCIES_ROOT_DIR/"
