This is an initial release of a solution for getting youtube running on the google glass. It is composed of 3 main components :

* A firefox extension that sends a youtube URL to a python server
* A Python server that listens for URLs from the firefox extension, and serves the video stream
* The Glass app that connects to the python server and displays the video stream.

The IP of your python server needs to be set inside of the firefox extension and in the glass app. 
The firefox extension should have a configuration area after installing it that allows you to change it. 

Changing it inside of the glass app can be done in two ways : you can change it inside of the main project file, or after installing it and launching it, you can long press on the side of the glass to activate voice recognition, where you can then speak the IP. Right now the port is hardcoded as 5000.
