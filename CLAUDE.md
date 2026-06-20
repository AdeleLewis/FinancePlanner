# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A bare IntelliJ IDEA Java project using **OpenJDK 26** with no build tool (no Maven or Gradle). The single source file lives in `src/Main.java`.

## Building and Running

This project has no build script. Compile and run from the terminal:

```powershell
# Compile
javac --release 26 src/Main.java -d out

# Run
java -cp out Main
```


Or open the project in IntelliJ IDEA and use the Run button — the `.iml` and `.idea/` config are already set up for `openjdk-26`.

## Language Notes

`src/Main.java` uses Java 26's **unnamed main method** feature (no wrapping class required) and `IO.println` (the companion I/O API introduced alongside unnamed mains). This is standard Java 26 behavior, not a custom utility.

