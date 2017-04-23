#!/bin/sh
#
# Author: David Goldfarb (deg@degel.com)
# Copyright (c) 2017, David Goldfarb

# This script is for the sake of our current deployment hack:
# The AWS image is too small to build in sane time, so we do a
#  lein cljsbuild once min
# locally and then sftp it to ~/cljtoys-Receipts/client/resources/public/js/compiled/app.js

docker build -t receipts-client-server-image .
./run_client_server.sh
