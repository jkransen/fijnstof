# Running on Raspberry Pi

Following is a step by step guide to build the project and install it on a fresh Raspberry Pi.

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
    - Interfaces -> Serial
        - Disable terminal over serial
        - Enable hardware device for serial
    - Expand filesystem, to take advantage of the entire SD card storage
    
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

## Alternative 1: build Debian package from sources
    
The Debian package will mainly add startup on boot functionality, which will be harder to achieve otherwise.

    sbt debian:packageBin

From your host computer, copy the Debian package, using the chosen hostname from `raspi-config` above:

    scp target/fijnstof_1.2_all.deb pi@fijnstof.local:

Note the trailing colon above.

On the Pi, install the package 
   
    sudo dpkg -i fijnstof_1.2_all.deb
    
The installation will add a fijnstof user and a start script that will launch at boot. 
It should also add the fijnstof user to the group `dialout`, which is needed to gain access to the serial device. 
Please check if this is successful, or add it manually: 

    sudo usermod -a -G dialout fijnstof
    
### Running the program

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

## Alternative 2: Run .jar file directly 

Make sure you user is added to the group `dialout` to access the serial devices.

    sudo usermod -a -G dialout pi
    
Run command, passing config file:

    java -jar fijnstof-assembly-1.2.jar -Dconfig.file=/etc/fijnstof/application.conf [list|test]
