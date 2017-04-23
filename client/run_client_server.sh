#!/bin/sh
#
# Author: David Goldfarb (deg@degel.com)
# Copyright (c) 2017, David Goldfarb

docker kill receipts-client-server-container
docker rm receipts-client-server-container

docker run --name receipts-client-server-container -d -p 80:80 receipts-client-server-image

# docker logs -f receipts-client-server-container
