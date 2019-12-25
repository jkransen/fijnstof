# Configuration

Below is a typical configuration for a setup with two sensors. It outputs to the same Domoticz instance, 
each on their own virtual sensor Idx:

    devices = [
      {
        device = ttyUSB0
        type = SDS011
        interval = 90
    
        domoticz {
          host = domoticz.local
          port = 8181
          pm25Idx = 1
          pm10Idx = 2
        }
    
        luftdaten {
          id = fijnstof-12345
        }
      },
      {
        device =  ttyUSB1
        type = MHZ19
    
        domoticz {
          host = domoticz.local
          port = 8181
          co2Idx = 3
        }
      }
    ]
    
## device

On Linux, serial devices on USB are typically named `ttyUSB0`, `ttyUSB1`. On Raspberry Pi, 
when connecting your sensor to the GPIO header's Rx/Tx pins ([pins 10 and 8 respectively](https://pinout.xyz/)), 
you'll need to use device `ttyAMA0`. This is the device you need to select. Also, connect ground (GND) and 
+3.3V connectors, 

On Mac, the USB adapters that come with the SDS011 / SDS021 sensors need an additional driver installed. 
[After installation and reboot](https://kig.re/2014/12/31/how-to-use-arduino-nano-mini-pro-with-CH340G-on-mac-osx-yosemite.html), you can hopefully
see an additional serial device, something like `cu.wchusbserialfd130` under `/dev`.
Using the specified driver, set the _device_ value to  `cu.wchusbserialfa130` or `cu.wchusbserialfa120`, whatever showed up.

On Windows, this would probably be something like `COM4`. 

## type

This should be `SDS011` for either _SDS011_ or _SDS021_, or `MHZ19` for _MH-Z19_ or _MH-Z19B_.

## interval

The amount of seconds to aggregate measurements and send as one. The average of the collected measurements is taken. 
Time interval is best effort, don't count on precision.

## domoticz

See [Domoticz](./README.md#Domoticz) for configuration of Domoticz itself.

- _host_, _port_  IP address or hostname and port of the running Domoticz installation
- _pm25Idx_, _pm10Idx_, _co2Idx_ The IDX of the virtual sensors that you added in Domoticz

## luftdaten

See [Luftdaten](./README.md#Luftdaten) for information about Luftdaten.

Set this element (including the curly braces) to send _PM2.5_ and _PM10_ values to Luftdaten.
- _id_ is optional, if you want to override the generated ID, or if no ID gets generated.
Check lhe logging for the generated ID. 

