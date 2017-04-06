#!/bin/sh
#
# Author: David Goldfarb (deg@degel.com)
# Copyright (c) 2017, David Goldfarb

# Derived from the initial README.md of this project:
# https://github.com/cognitect-labs/vase/blob/master/template/src/leiningen/new/vase/README.md


lein clean
lein uberjar

docker build -t receipts-server-image .

docker kill receipts-server-container
docker rm receipts-server-container

docker run --name receipts-server-container -d -p 8080:8080 receipts-server-image

docker logs -f receipts-server-container

