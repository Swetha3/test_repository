#!/bin/bash
set -e

for p in `lsof -i :3000,3449,3450,4443,4444,4445 | sed '1d' | cut -d " " -f 5`;
do
    kill -9 $p
done
