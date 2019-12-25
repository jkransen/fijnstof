## Introduction

This app periodically reads physically attached sensors (Serial/UART) and distributes readings to external services.

Currently supported sensors: 

- Particulates
  - SDS011
  - SDS021 (Not recommended for outdoor use)
- CO<sub>2</sub>
  - MH-Z19 (B)

Currently supported external services:

- Domoticz
- Luftdaten.info

## Packaging

_Skip this section if you have a _fijnstof_1.2_all.deb_ file ready to install._

Run this command to create a Debian package (.deb):

    sbt debian:packageBin
    
The result will be this file:   

    target/fijnstof_1.1_all.deb
    
    echo "deb http://kransen.nl/repo binary" > /etc/apt/sources.list.d/myrepo.list
    apt-get update

## Installation

This package is targeted at the Raspberry Pi running Raspbian, but the software runs without changes on any Linux or Mac 
with a sensor on a serial device. It may even run under Windows, please let me know if you get this working! Running
`fijnstof list` (or `sbt "run list"` from the sources) should list the serial devices on Windows as well. 

See the [Raspberry Pi](RaspberryPi.md) section for instructions how to install it on this platform.
 
        
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

Instead of installing Domoticz, you can run one in Docker

    docker-compose up domoticz

In Domoticz -> Settings -> Hardware, add new Dummy hardware

- For the SDS011
  - On the Dummy hardware, create 2 virtual sensors, `PM2.5` and `PM10`. 
  - Sensor type: _Custom sensor_. Enter (copy/paste) `µg/m³` for axis label. 
- For the MH-Z19
  - On the Dummy hardware, create 1 virtual sensor, `CO₂`. 
  - Sensor type: _Air quality_

Look up the assigned _IDX_ values under _Devices_ (order by IDX descending), and set them in the 
[configuration file](Configuration.md). Probably these are `1`, `2` and `3` on a clean Domoticz install.

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


