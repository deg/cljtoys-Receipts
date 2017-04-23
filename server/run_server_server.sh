#!/bin/sh
#
# Author: David Goldfarb (deg@degel.com)
# Copyright (c) 2017, David Goldfarb

docker kill receipts-server-container
docker rm receipts-server-container

docker run --name receipts-server-container -d -p 8080:8080 receipts-server-image

# docker logs -f receipts-server-container
