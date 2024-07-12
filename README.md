# Kickr Shiftr Android

Inspired by https://github.com/jvivenot/KickrShiftr, I want to be able to shift to harder gears in Rouvy from my android device, especially when going down negative gradient slopes. 

An easy way to simulate this is by changing the configured wheel circumference of the trainer.

I have a Wahoo Kickr V5 which uses a slightly different interface to the Wahoo Kickr Core that jvivenot is using.

It should be pretty easy for others to add support for their own Kickr version, or even another trainer altogether, by dumping the bluetooth snoop log and working out what service and characteristic are being used to write the wheel circumference.

I may add more configurability when I get the time.

# Screenshots
![screenshot](screenshots/screenshot.jpg?raw=true "Screenshot of the app interface")
# License
I've used https://github.com/PunchThrough/ble-starter-android as a base for this project.
This project is licensed under the Apache 2.0. For more details, please see [LICENSE](https://github.com/PunchThrough/ble-starter-android/blob/master/LICENSE).
