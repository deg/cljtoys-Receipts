#!/bin/sh
#
# Author: David Goldfarb (deg@degel.com)
# Copyright (c) 2017, David Goldfarb

# Derived from https://github.com/Day8/re-frame-template/issues/20

git pull

lein clean
lein cljsbuild once min
lein garden once

docker build -t receipts-client-server-image .
