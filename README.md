# Receipts

Receipts management program.

This project serves two goals, which I hope won't conflict too much with each other:

1) A tool for my family to record receipts from their phones, where Excel is far too clumsy
2) A testbed for me to play with Vase, Datomic, re-frame, etc.

# Design Strategy

This repo contains two Clojure projects: the server and client. This was an intentional
experiment in separating concerns: creating as much isolation as reasonable between the
client and server. But, see below, I'm not 100% happy with how this played out.

## Server

The server is a [Vase](https://github.com/cognitect-labs/vase) project. It is mostly
vanilla, with two interesting bits where I was exploring the tools:

- In `server/src/receipts_server/interceptors.clj` I play with some ideas to reduce the
  verbosity of interceptors, at least relative to the sample apps in the Vase repo.
- `src/receipts/specs.cljc` is hard-linked to the same file in the client project
  (discussed below).

## Client

The client is built on
the [re-frame template](https://github.com/Day8/re-frame-template) and was initialized
with `lein new re-frame receipts-client +cider +test +garden +re-com +routes +re-frisk`.

This project's source contains a tiny server component `client/src/clj/` with just the
bare skeleton to serve the client. Most of this project is pure cljs.

## Shared code

Certain code needs to be shared between the client and server; notably the
`clojure.spec` definitions. This should rightly be a third project. For now, I wanted to
avoid the time and cognitive overhead of finding the best way to set this up, especially
since this component is currently just one small file. Instead, the file `specs.cljs`
exists in both projects. On my computer, the two are hard-linked to one file, at the
filesystem level. If you check this project out of github, you will not get this
magic. If you care, and are not grossed out by the hack, you can duplicate it by:

```
cd client/src/cljs/receipts
rm specs.cljc
ln ../../../../receipts/server/src/receipts/specs.cljc specs.cljc
```

# Usage

## Web page

If you follow the launch instructions below, the development server will run at
`localhost:3449`. The production server is wherever you want to put it; see instructions
below.

## Pages

This is a single-page application, with multiple tabs:

- Receipt: Enter a new receipt
- Edit: Add to the schema
- History: show all receipts that have been entered
- About: Usual cruft
- Setup: Admin tools

# Building

## Development server

### Building server

- cd server
- lein repl
- in repl: `(def dev-serv (run-dev))`

### Building client and running

- Open one of the .cljs files in Emacs, and give the usual C-M-Sh-J salute
- Browse to localhost:3449

## Production server

 I use a very lightweight AWS instance: a Lightsail OS-Only Ubuntu 16.04 LTS instance
 with the cheapest settings (512 MB RAM, 1 vCPU, 20 GB SSD).

The production build is driven by scripts in my [lightsail-config](https://github.com/deg/lightsail-config) project.

The `remote_build` script in that project will rebuild this project and upload it to the server

Then connect to the server (e.g., `ssh -i ~/.ssh/DEG-Amazon-Lightsail-Key.pem ubuntu@lightsail-1.degel.com` for me) and:
```
cd lightsail-config
./start_server
```

Browse to port 80 on the server.

# Running

## Currently

I am still using an in-mmeory Datomic DB. It is pre-populated with the schema and just one user.

The initial user is `admin@degel.com`. The initial password is obviously secret, and is
hashed in `server/resources/receipts-server_service.edn`. Contact me if you need the
password or simpply change the hash there. (You can generate a new hash with
`receipts-server.interceptors/encrypt`).  If you are me, then there is a clue in that
file that should remind me of the correct password.

You will need to login as `admin@degel.com`, go to the `setup` tab, and `load entities`
from an edn file that includes all the entities you want to preload. If you are me, you
can find the latest such file at
`~/CentralPark/users/Family/models/receipts-preload.edn`.  Otherwise, contact me, or
create a file similar to:

```
{:user/name "David Goldfarb", :user/isConsumer true, :user/abbrev "D", :user/email "deg@degel.com", :user/isEditor true, :user/isAdmin true, :user/passwordEncrypted "<FILL IN ...>"}

{:source/name "Credit 1", :source/abbrev "v1234"}
{:source/name "Cash", :source/abbrev "Cash"}

{:currency/name "US Dollars", :currency/abbrev "USD"}
{:currency/name "Euros", :currency/abbrev "EU"}
{:currency/name "GB Pounds", :currency/abbrev "GBP"}
{:currency/name "New Israeli Shekels", :currency/abbrev "NIS"}

{:category/name "Books", :category/description "Books, e-books, other media"}
{:category/name "Car", :category/description "Car, parts, and service"}
{:category/name "Travel", :category/description "Airfare, bus, taxi, and other travel-related"}

{:vendor/category,  ["Books"], :vendor/name "Steimatzky"}
{:vendor/category,  ["Books Travel"], :vendor/name "Amazon"}
{:vendor/category,  ["Car"], :vendor/name "Parking"}
{:vendor/category,  ["Travel"], :vendor/name "Bus"}
{:vendor/category,  ["Travel"], :vendor/name "Taxi"}
{:vendor/category,  ["Travel"], :vendor/name "Train"}

```

You can also populate the database incrementally in the `edit` tab. You can then extract
the results from the `schema` section of the `setup` tab, and save them to a local
`.edn` file.

## Future

This should connect to a persistent DB running on a different server.

... not yet tested ...
To start Datomic
```
~/datomic/datomic-pro-0.9.5561/bin/transactor ~/Documents/git/projects/lightsail-config/dev-transactor-with-license.properties
```


# Contributing

This is mostly a personal learning project. But, if you find it useful and want to help
it grow, I'm open to code contributions. Best to speak with me first; open an issue and
I'll get back to you.

# License

Licensed under the Eclipse Public License.

Copyright (c) 2017, David Goldfarb <deg@degel.com>
Portions based on earlier versions of this application, also written by me, copyright 2013-2016
