# Receipts

Receipts management program.

This project serves two goals, which I hope won't conflict too much with each other:

1) A tool for my family to record receipts from their phones, where Excel is far too clumsy
2) A testbed for me to play with Vase, Datomic, re-frame, etc.


# Usage

## Web page

TBD

## Pages

TBD

### Query Parameters

TBD

- Server

TBD

## Customization

# Building

## Development server

### Building server

- cd server
- lein repl
- in repl: ``

### Building client

- Open one of the .cljs files in Emacs, and give the usual C-M-Sh-J salute
- Browse to localhost:3449

## Production server

The production build is driven by scripts in my [lightsail-config](https://github.com/deg/lightsail-config) project.

The remote_build.sh script in that project will rebuild this project and upload it to the server


# Contributing

This is mostly a personal learning project. But, if you find it useful and want to help
it grow, I'm open to code contributions. Best to speak with me first; open an issue and
I'll get back to you.

# License

Licensed under the Eclipse Public License.

Copyright (c) 2017, David Goldfarb <deg@degel.com>
Portions based on earlier versions of this application, also written by me, copyright 2013-2016
