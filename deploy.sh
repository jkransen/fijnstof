sbt debian:packageBin
scp target/fijnstof_1.0_all.deb pi@fijnstof:
ssh pi@fijnstof sudo dpkg -i fijnstof_1.0_all.deb
ssh pi@fijnstof tail -f /var/log/daemon.log

