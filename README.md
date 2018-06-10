## Introduction

This package reads sensors and distributes readings to external services.

Currently supported sensors: 

- SDS011
- SDS021

Currently supported external services:

- Domoticz

## Packaging

Run this command to create a Debian package (.deb):

    sbt debian:packageBin
    
The output will be a file:   

    target/fijnstof_(version)_all.deb

## Installation

This package is targeted at the Raspberry Pi running Raspbian, but the software should run on any Linux with a sensor on a serial device. 

Following is a step by step guide to install a Raspberry Pi with fijnstof running:

- Download the latest Raspbian, and extract it to get the .img
- dd if=yourimage.img of=/dev/wherever the sd card is bs=8M
- sync
- eject and mount again on your running linux machine
- add an empty file called `ssd` in the boot partition. This will allow ssd to be enabled at first boot, so you don't need to attach keyboard or monitor.
- at boot, configure at least the folowing:
    - password, something other than `raspberrypi`
    - hostname, eg `fijnstof`
    - boot into CLI, without logging in
    - No splash screen
    - Wifi country, SSID and password (if you don't want/have an ethernet cable available)
    - Locale, keyboard layout
    
Check your router for the assigned IP address.

    
From your host computer, copy the Debian package:

    scp target/fijnstof*.deb 

On the Pi, install the package 
   
    sudo dpkg -i fijnstof*.deb
    
The installation will add a fijnstof user and a start script that will launch at boot. It should also add the fijnstof user to the group `dialout`, which is needed to gain access to the serial device. Please check if this is successful, or add it manually: 

    sudo usermod -a -G dialout fijnstof
    
Configuration is in:

    /etc/fijnstof/application.conf
    
Set the serial device here, the host/port of the target Domoticz installation.

In Domoticz, create new hardware -> Dummy

From Dummy hardware, create 2 virtual sensors. Look up the assigned IDX values, and set them in the above configuration file.

You can add the new devices to the floor plan, and drag them to the correct phyisical place in the right room.