#!/bin/bash
cd ./server/target/ && tar -xvf tpe1-g7-server-1.0-SNAPSHOT-bin.tar.gz && cd ../..
cd ./server/target/tpe1-g7-server-1.0-SNAPSHOT/ 
chmod 700 run-registry.sh
chmod 700 run-server.sh && cd ../../..

cd ./client/target && tar -xvf tpe1-g7-client-1.0-SNAPSHOT-bin.tar.gz && cd ../..
cd ./client/target/tpe1-g7-client-1.0-SNAPSHOT
chmod 700 run-query.sh
chmod 700 run-airline.sh
chmod 700 run-management.sh
chmod 700 run-runway.sh