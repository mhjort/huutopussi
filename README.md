# huutopussi

Multiplayer (4 players) card game: https://fi.wikipedia.org/wiki/Huutopussi

This repository contains both client and server for running the code.

Note! Project is still in alpha stage!

## Prerequisites

You will need [Leiningen][] 2.9.1 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

### Development mode (auto reload & REPL)

To start a web server for the application (runs in port 3000), run:

    lein ring server-headless

To start a client. Go to client directory and run:

    lein fig:build

Open http://localhost:9500 to play

### Production build

Create production uberjar:

    bin/build

Run it

    java jar target/huutopussi-standalone.jar

Open http://localhost:3000 to play

## License

Copyright © 2020-2021 Markus Hjort
