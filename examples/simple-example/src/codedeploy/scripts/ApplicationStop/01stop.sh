#!/bin/bash

PID="$(pgrep -f ExampleMain)"
while sleep 0.2
      echo Testing again
      kill $PID >/dev/null 2>&1
do
    # Code to kill process
    echo "Shut down ExampleMain"
done
