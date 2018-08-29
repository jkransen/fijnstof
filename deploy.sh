#!/bin/bash
PI=fijnstof
if [ $# -gt 0 ]; then
  PI=$1
fi
echo "Deploying to $PI"
sbt clean debian:packageBin
scp target/fijnstof_1.0_all.deb pi@$PI:
ssh pi@$PI sudo dpkg -i fijnstof_1.0_all.deb
ssh pi@$PI sudo service fijnstof start
ssh pi@$PI tail -f /var/log/fijnstof/fijnstof.log

