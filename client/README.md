# huutopussi-client

This is huutopussi client project implemented using https://github.com/Day8/re-frame

## Development

To get an interactive development environment run:

    lein fig:build

This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

	lein clean

To create a production build run:

	lein clean
	lein fig:min

## License

Copyright © 2021 Markus Hjort

Distributed under the Eclipse Public License either version 1.0.
