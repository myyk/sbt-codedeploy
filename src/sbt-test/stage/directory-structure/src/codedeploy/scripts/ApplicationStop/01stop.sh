#!/bin/bash

if pkill -f ExampleMain; then
    echo "Shut down ExampleMain"
else
    echo "ExampleMain was not running"
fi