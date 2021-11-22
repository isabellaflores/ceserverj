# ceserverj
Ceserverj is a server for Cheat Engine on Windows. It was developed independently from the Cheat Engine software
by DarkByte, and is not affiliated with Cheat Engine.

# Benefits
* Can be run on your target machine, while the Cheat Engine interface is run on second machine, or even in a
virtual machine. This can be useful in cases where you don't want to run Cheat Engine on the target machine.
* 100% pure Java.
* Console-based, so it does not create any windows.

# Caveats
* Just like Cheat Engine itself, it uses ReadProcessMemory and WriteProcessMemory, which may be detected by target processes.

# Installation Option #1 - Downloading the Binary
It is *recommended* that you build ceserverj yourself from its source code:

1. Download and install [Temurin 11](https://adoptium.net/?variant=openjdk11) (Any other Java 11+ is fine too)
2. Download [the latest ceserverj.jar](https://github.com/isabellaflores/ceserverj/releases) from Github
3. Copy the downloaded ceserverj.jar file to your Desktop
4. Continue with "Running the Server" section below

# Installation Option #2 - Building from Source
It is *recommended* that you build ceserverj yourself from its source code:

1. Download and install [Temurin 11](https://adoptium.net/?variant=openjdk11) (Any other Java 11+ is fine too)
2. Download and install [Apache Maven](https://www.youtube.com/watch?v=--Iv5vBIHjI)
3. Download [the latest source code](https://github.com/isabellaflores/ceserverj/releases) from Github
4. Unzip the source code to any desired location
5. Build it using Maven by typing "mvn package" in the source code directory
6. Copy the ceserverj.jar file from the 'target' directory to your Desktop
7. Continue with "Running the Server" section below

# Running the Server
1. Open a command prompt (Win+R then type "cmd")
2. Type "cd Desktop"
3. Type "java -jar ceserverj.jar"
4. The server will now be listening on the default port, 52736.

# Connecting to the server
1. Open Cheat Engine
2. File -> Open Process
3. Click 'Network'
4. Type the hostname or ip address of the server
5. Click 'Connect' and select a process to open

# Contributing to ceserverj
Thank you for your interest in contributing to ceserverj!

To submit your changes to me, please [create a pull request](https://github.com/isabellaflores/ceserverj/pulls), and I will personally review your submission. If it is
accepted, you will receive credit for your submission. If you'd like your submission to be anonymous or pseudonymous,
please let me know.