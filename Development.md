# Development

## Run from CLI during development

You can manually start the application from the command prompt. Without options, it will run as in daemon mode,
indefinitely reading and outputting, until you press Ctrl-C.

    sbt run
    
To get a list of available serial devices, enter

    sbt "run list"
    
To test the setup with 1 measurement, enter

    sbt "run test"
    
Note the difference with the regular command to run the software tests

    sbt test

## Packaging

_Skip this section if you have a _fijnstof_1.2_all.deb_ file ready to install._

Run this command to create a Debian package (.deb):

    sbt debian:packageBin
    
The result will be this file:   

    target/fijnstof_1.2_all.deb
    

