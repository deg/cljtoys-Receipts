#!/bin/sh
#
# Author: David Goldfarb (deg@degel.com)
# Copyright (c) 2017, David Goldfarb

# This script is for the sake of our current deployment hack:
# The AWS image is too small to build in sane time, so we do a
#  lein uberjar
# locally and then sftp it to ~/cljtoys-Receipts/server/target/receipts-server-0.0.1-SNAPSHOT-standalone.jar

docker build -t receipts-server-image .
./run_server_server.sh
