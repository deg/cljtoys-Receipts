#!/bin/sh
#
# Author: David Goldfarb (deg@degel.com)
# Copyright (c) 2017, David Goldfarb

# Derived from https://github.com/Day8/re-frame-template/issues/20


lein clean
lein cljsbuild once min

docker build -t receipts-client-server-image .

docker kill receipts-client-server-container
docker rm receipts-client-server-container

docker run --name receipts-client-server-container -d -p 3000:80 receipts-client-server-image

docker logs -f receipts-client-server-container
