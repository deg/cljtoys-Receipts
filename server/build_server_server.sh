#!/bin/sh
#
# Author: David Goldfarb (deg@degel.com)
# Copyright (c) 2017, David Goldfarb

git pull

lein clean
lein uberjar

docker build -t receipts-server-image .

