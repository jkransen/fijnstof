# MH-Z19

See [doc](doc/MH-Z19%20CO2%20Ver1.0.pdf)

The MH-Z19(B) sensor can be purchased online. It costs around €20, US$20. Note that it comes in slightly varying form factors. 
If it comes with a pin header, or if you need to solder it yourself, then the labels on the board are obvious.
If you purchase the version with wire, then follow this layout for wiring:


| Colour  | Function       | Level |
|---------|----------------|-------|
| yellow
| green   | UART Tx        | 3.3V  |
| blue    | UART Rx        | 3.3V  |
| red     | Vin            | 5V    |
| black   |	GND            | 0V    |
| white
| brown

For testing purposes, I cut the wire, as well as the USB adapter for a SDS021, and soldered them together. 
There are lots of adapters, like PL2303, FT232 etc. Most are supported out of the box by Linux, and share the levels above.
Remember to wire the Tx of each to the Rx of the other, GND to GND, and Vin to Vin. 