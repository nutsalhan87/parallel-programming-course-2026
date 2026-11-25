#!/usr/bin/env bash

./build.sh
java -cp out lab3.Main -- -- "$@"