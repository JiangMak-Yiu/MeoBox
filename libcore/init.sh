#!/bin/bash

chmod -R 777 .build 2>/dev/null
rm -rf .build 2>/dev/null

if [ -z "$GOPATH" ]; then
    GOPATH=$(go env GOPATH)
fi

# Install gomobile
if [ ! -f "$GOPATH/bin/gomobile-matsuri" ] && [ ! -f "$GOPATH/bin/gomobile-matsuri.exe" ]; then
    git clone https://github.com/MatsuriDayo/gomobile.git
    pushd gomobile
	git checkout origin/master2
    pushd cmd
    pushd gomobile
    go install -v
    popd
    pushd gobind
    go install -v
    popd
    popd
    rm -rf gomobile
    if [ -f "$GOPATH/bin/gomobile.exe" ]; then
      mv "$GOPATH/bin/gomobile.exe" "$GOPATH/bin/gomobile-matsuri.exe"
    else
      mv "$GOPATH/bin/gomobile" "$GOPATH/bin/gomobile-matsuri"
    fi
    if [ -f "$GOPATH/bin/gobind.exe" ]; then
      mv "$GOPATH/bin/gobind.exe" "$GOPATH/bin/gobind-matsuri.exe"
    else
      mv "$GOPATH/bin/gobind" "$GOPATH/bin/gobind-matsuri"
    fi
fi

if [ -f "$GOPATH/bin/gomobile-matsuri.exe" ]; then
  GOBIND="$GOPATH/bin/gobind-matsuri.exe" "$GOPATH/bin/gomobile-matsuri.exe" init
else
  GOBIND=gobind-matsuri gomobile-matsuri init
fi
