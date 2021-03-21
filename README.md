# huutopussi

Multiplayer (4 players) card game: https://fi.wikipedia.org/wiki/Huutopussi

This repository contains both client and server for running the code.

Note! Project is still in alpha stage!

## Prerequisites

You will need [Leiningen][] 2.9.1 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

### Development mode (auto reload & REPL)

In development mode assets (images and JS) are served from client and server is only API.
To start a web server (Rest API) for the application (runs in port 3000), run:

    lein ring server-headless

To start a client. Go to client directory and run:

    lein fig:build

Open http://localhost:9500 to play

### Production build

Production build compiles minimal JS file and copies it and other assets (images) to server.
Then the server is built as one standalone uber jar.

Create production uberjar:

    bin/build

Run it

    java -jar target/huutopussi-standalone.jar

Open http://localhost:3000 to play

## License

Copyright © 2020-2021 Markus Hjort

Distributed under the Eclipse Public License version 1.0.
