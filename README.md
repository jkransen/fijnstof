## Introduction

This app periodically reads physically attached sensors (Serial/UART) and distributes readings to external services.

Currently supported sensors: 

- SDS011
- SDS021

Currently supported external services:

- Domoticz
- Luftdaten.info

## Packaging

Run this command to create a Debian package (.deb):

    sbt debian:packageBin
    
The result will be this file:   

    target/fijnstof_1.0_all.deb

## Installation

This package is targeted at the Raspberry Pi running Raspbian, but the software should run on any Linux with a sensor on a serial device, 
probably even on a Mac, and maybe even under Windows (please let me know if you get this working!). The Debian package will
mainly add startup on boot functionality, which will be harder to achieve otherwise.  

### Raspberry Pi

Skip this section if you have a running Pi. Following is a step by step guide to install a Raspberry Pi with (a local build) of fijnstof running:

- Download the latest Raspbian, and extract it to get the .img
- `dd if=yourimage.img of=/dev/wherever the sd card is bs=8M`
- `sync`
- Eject and mount again on your running linux machine
- Add an empty file called `ssh` in the boot partition. This will allow ssh to be enabled at first boot, so you don't need to attach keyboard or monitor.
- On boot, run `sudo raspi-config` and configure at least the following:
    - password, something other than `raspberrypi`
    - hostname, eg `fijnstof`
    - boot into CLI, without logging in
    - No splash screen
    - Wifi country, SSID and password (if you don't want/have an ethernet cable available)
    - Locale, keyboard layout
    - Expand filesystem, to take advantage of the entire SD card storage
    
Check your router for the assigned IP address, or try `fijnstof.local` or whatever hostname you chose instead.

### Optional: Add authorized SSH key

To avoid having to enter your password for every _ssh_ or _scp_, you can add an SSH public key to the pi. 

First make sure you have a public key

    ls -l ~/.ssh/id_rsa.pub

If no key exists, create one:

    ssh-keygen -t rsa -b 4096 -C "your_email@example.com"

Copy the _public key_ (only) to the pi:

    scp ~/.ssh/id_rsa.pub pi@fijnstof.local:

On the pi, check if there is no existing `authorized_keys` file present:

    ls -l ~/.ssh/authorized_keys

If not present, create and move:

    mkdir ~/.ssh/
    mv ~/id_rsa.pub ~/.ssh/authorized_keys

Otherwise, just add the key:

    cat ~/id_rsa.pub >> ~/.ssh/authorized_keys

### Debian package
    
From your host computer, copy the Debian package, using the chosen hostname from above:

    scp target/fijnstof_1.0_all.deb pi@fijnstof.local:

On the Pi, install the package 
   
    sudo dpkg -i fijnstof_1.0_all.deb
    
The installation will add a fijnstof user and a start script that will launch at boot. 
It should also add the fijnstof user to the group `dialout`, which is needed to gain access to the serial device. 
Please check if this is successful, or add it manually: 

    sudo usermod -a -G dialout fijnstof
    
Configuration is in:

    /etc/fijnstof/application.conf
    
Set the serial device here, the host/port of the target Domoticz installation.

Start the service

    sudo service fijnstof start
    
Logging should appear in the daemon log

    tail -f /var/log/daemon.log

### Domoticz

In Domoticz -> Settings -> Hardware, add new Dummy hardware

On the Dummy hardware, create 2 virtual sensors, `PM2.5` and `PM10`. 
Sensor type: _Custom sensor_. Enter (copy/paste) `µg/m³` for axis label. 
Look up the assigned _IDX_ values under _Devices_ (order by IDX descending), and set them in the above configuration file.

You can add the new devices to the _floor plan_ if you have one, and drag them to the correct physical place in the right room.

### Luftdaten

[Luftdaten.info](http://luftdaten.info) is a Citizen Science project to collect as many particulate sensor data as possible, and show it on a map. 
This project was specifically created with the idea in mind to make contribution as easy as possible. 
If the sensor is _outside_ (nobody cares about your indoor readings), you may consider connecting to the luftdaten.info API. 
Once you retrieved the ID of your sensor from the logging (eg fijnstof-12345), use that to register at the bottom of [their DIY page](https://luftdaten.info/en/construction-manual/). 
After some time (days), you  will get a confirmation, and you will see the measurements on the map on your specified 
location: [maps.luftdaten.info](http://maps.luftdaten.info) (slightly off, for privacy reasons), but also anyone else interested in particulate rates in their area or anywhere else.


