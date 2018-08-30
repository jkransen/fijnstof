## Introduction

This app periodically reads physically attached sensors (Serial/UART) and distributes readings to external services.

Currently supported sensors: 

- Particulates
  - SDS011
  - SDS021 (Deprecated! For indoor use only)
- CO<sub>2</sub>
  - MH-Z19 (B)

Currently supported external services:

- Domoticz
- Luftdaten.info

## Packaging

_Skip this section if you have a _fijnstof_1.1_all.deb_ file ready to install._

Run this command to create a Debian package (.deb):

    sbt debian:packageBin
    
The result will be this file:   

    target/fijnstof_1.1_all.deb
    
    echo "deb http://kransen.nl/repo binary" > /etc/apt/sources.list.d/myrepo.list
    apt-get update

## Installation

This package is targeted at the Raspberry Pi running Raspbian, but the software runs without changes on any Linux or Mac 
with a sensor on a serial device. It may even run under Windows, please let me know if you get this working! `sbt list` should list the
 serial devices on Windows as well. 
 
### Raspberry Pi

_Skip this section if you have a running Pi._

Following is a step by step guide to install a Raspberry Pi with (a local build) of fijnstof running:

- Download [the latest Raspbian](https://www.raspberrypi.org/downloads/raspbian/), and extract it to get the .img
- `sudo fdisk -l` to determine the block device of your SD card (compare sizes to what you inserted)
- `sudo dd if=yourimage.img of=/dev/mmcwherever bs=8M`
- `sync`
- Eject and mount again on your running linux machine
- Add an empty file called `ssh` in the boot partition. This will allow ssh to be enabled at first boot, so you don't need to attach keyboard or monitor.
- Check your router for the assigned IP address, or try `raspberrypi.local`.
- `ssh pi@raspberrypi`, replace _raspberrypi_ with whatever hostname or IP address was assigned
- On boot, run `sudo raspi-config` and configure at least the following:
    - password, something other than `raspberrypi`
    - hostname, from here, we will assume `fijnstof`
    - boot into CLI, without logging in
    - No splash screen
    - Wifi country, SSID and password (if you don't want/have an ethernet cable available)
    - Locale, keyboard layout
    - Expand filesystem, to take advantage of the entire SD card storage
    
### Optional: Add authorized SSH key

_Skip this section if you don't mind entering passwords._

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
    
The Debian package will mainly add startup on boot functionality, which will be harder to achieve otherwise.  

From your host computer, copy the Debian package, using the chosen hostname from above:

    scp target/fijnstof_1.1_all.deb pi@fijnstof.local:

Note the trailing colon above.

On the Pi, install the package 
   
    sudo dpkg -i fijnstof_1.1_all.deb
    
The installation will add a fijnstof user and a start script that will launch at boot. 
It should also add the fijnstof user to the group `dialout`, which is needed to gain access to the serial device. 
Please check if this is successful, or add it manually: 

    sudo usermod -a -G dialout fijnstof
    
List all available serial devices, and check if the one you expect to represent your device is among them:

    fijnstof list
    
Take out the device, and see if it disappears from the list when running again. Then plug it back in.
    
Configuration is in:

    /etc/fijnstof/application.conf
    
It may be self-explanatory. If not, see the dedicated [configuration documentation](Configuration.md) for more extensive information.
    
Set the serial device here. Enable blocks for Domoticz or Luftdaten, whichever you want to use. Make a test run:

    fijnstof test
    
See if you get any errors. Write down the machine id, something like _fijnstof-e123456e_. You will need this number later.

Start the service

    sudo service fijnstof start
    
Logging should appear in the daemon log

    tail -f /var/log/daemon.log
        
### Run from CLI during development

You can manually start the application from the command prompt. Without options, it will run as in daemon mode,
indefinitely reading and outputting, until you press Ctrl-C.

    sbt run
    
To get a list of available serial devices, enter

    sbt "run list"
    
To test the setup with 1 measurement, enter

    sbt "run test"
    
Note the difference with the regular command to run the software tests

    sbt test

### Domoticz

Domoticz is a system to automate your home. It supports a variety of protocols and devices, allowing you to control your home and respond to sensor values. 
Domoticz also keeps track of historic readings, and presents them in diagrams. We can send our particulate measurements to Domoticz to track the trends over time.

In Domoticz -> Settings -> Hardware, add new Dummy hardware

- For the SDS011
  - On the Dummy hardware, create 2 virtual sensors, `PM2.5` and `PM10`. 
  - Sensor type: _Custom sensor_. Enter (copy/paste) `µg/m³` for axis label. 
- For the MH-Z19
  - On the Dummy hardware, create 1 virtual sensor, `CO2`. 
  - Sensor type: _Air quality_

Look up the assigned _IDX_ values under _Devices_ (order by IDX descending), and set them in the [configuration file](Configuration.md).

You can add the new devices to the _Floor Plan_ if you have one, and drag them to the correct physical place in the right room.

### Luftdaten

[Luftdaten.info](http://luftdaten.info) is a Citizen Science project to collect as many particle sensor data as possible, and show it on a map. 
This project was specifically created with the idea in mind to make contribution as easy as possible. 
If the sensor is _outside_ (nobody cares about your indoor readings), you may consider connecting to the luftdaten.info API. 
Once you retrieved the machine ID of your sensor from the logging (or by running `fijnstof test`, something like _fijnstof-e123456e_). 
Use that to register at the bottom of [their DIY page](https://luftdaten.info/en/construction-manual/). 
After some time (days), you  will get a confirmation, and you will see the measurements on the map on your specified 
location: [maps.luftdaten.info](http://maps.luftdaten.info) (slightly off, for privacy reasons), 
but also anyone else interested in particle rates in their area or anywhere else.


