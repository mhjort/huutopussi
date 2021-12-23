# huutopussi

[Build Status](https://github.com/mhjort/huutopussi/actions/workflows/clojure.yml/badge.svg)

Multiplayer (4 players) card game: https://fi.wikipedia.org/wiki/Huutopussi

This repository contains both client and server for running the code.

Note! Project is still in alpha stage!

## Prerequisites

You will need Java and [Leiningen](https://leiningen.org/) 2.9.1 or above installed.

## Easy Setup on MacOS

Run steps 1.-3. only on the first time.

1. Download and install Java from e.g. [AWS Corretto](https://corretto.aws/downloads/latest/amazon-corretto-11-x64-macos-jdk.pkg)

1. Install [Leiningen](https://leiningen.org/)

1. Clone this repository in a terminal

        git clone https://github.com/mhjort/huutopussi.git

1. Run the backend in a terminal

        cd huutopussi
        lein ring server-headless

2. Run the frontend in a separate terminal

        cd huutopussi/client
        lein fig:build

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
