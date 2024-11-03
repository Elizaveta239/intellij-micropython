The Changelog
=============

2.0.0.pre-alpha1 - 2024-11-03
------------------
NOVA approach: no internal python scripts, webrepl support, on-device file manager

The Changelog
=============

1.4.6 - 2024-08-28
------------------

* Support for 2024.2 IDE versions
  ([#295](https://github.com/JetBrains/intellij-micropython/issues/295))

1.4.5 - 2024-07-27
------------------

### Fixed

* Don't interrupt program execution when REPL is started right after the upload
  ([#313](https://github.com/JetBrains/intellij-micropython/pull/313))
* Update `machine.pyi`
  ([#291](https://github.com/JetBrains/intellij-micropython/pull/291))
  (Thanks to [Lucio Rossi](https://github.com/eigen-value))
* Update `network.pyi`
  ([#296](https://github.com/JetBrains/intellij-micropython/pull/296))

1.4.4 - 2024-07-13
------------------

### Fixed

* Compatibility with 2024.1.x IDEs
* REPL should work fine with the latest IDE versions

1.4.2 - 2023-10-11
------------------

### Fixed

* Metro M4 Airlift Lite support added
  ([#254](https://github.com/JetBrains/intellij-micropython/pull/254))
  (Thanks to [@PierreDeQuebec](https://github.com/PierreDeQuebec))
* Compatibility issue in REPL support is fixed
  ([#253](https://github.com/JetBrains/intellij-micropython/pull/253))
  (Thanks to [@lancelote](https://github.com/lancelote))

1.4.1 - 2023-03-17
------------------

### Added

* Type Hints for ESP32 added
  ([#218](https://github.com/JetBrains/intellij-micropython/pull/218))
  (Thanks to [@MrNavaStar](https://github.com/MrNavaStar))

1.4.0 - 2022-11-21
------------------

### Added

* MicroPython REPL running in a dedicated tool window 
  ([#139](https://github.com/JetBrains/intellij-micropython/pull/139))
  (Thanks to [@lensvol](https://github.com/lensvol))
* Support for the Micro:bit V2 device type
  ([#186](https://github.com/JetBrains/intellij-micropython/pull/186))

### Changed

* Compatibility with 2021.3-2022.3


1.3.3 — 2022-09-23
------------------

### Fixed

* Broken compatibility with the older PyCharm versions

1.3.2 — 2022-09-23
------------------

### Fixed

* An error on opening projects in PyCharm 2022.2.2
  ([#205](https://github.com/JetBrains/intellij-micropython/issues/205))


1.3.1 — 2022-03-18
------------------

### Changed

* Compatibility with 2020.3-2022.1
  ([#184](https://github.com/vlasovskikh/intellij-micropython/issues/184))


1.3 — 2021-12-01
----------------

### Added 

* Added many stubs for the MicroPython standard library and Pyboard
  ([#176](https://github.com/vlasovskikh/intellij-micropython/pull/176))
  (Thanks to [@hlovatt](https://github.com/hlovatt))

### Changed

* Compatibility with 2020.3-2021.3

### Fixed

* Fixed special keys in REPL on Windows for pyserial>=3.5
  ([#175](https://github.com/vlasovskikh/intellij-micropython/issues/175))


1.2 — 2021-08-05
----------------

### Added

* Added support for Raspberry Pi Pico devices
  ([#132](https://github.com/vlasovskikh/intellij-micropython/issues/132))
  (Thanks to [@timsavage](https://github.com/timsavage))
* Added type hints for `lcd160cr`, `network`, `ubluetooth`, `ucryptolib`, `uctypes`
  (Thanks to [@hlovatt](https://github.com/hlovatt))

### Changed

* Compatibility with 2020.3-2021.2
  ([#170](https://github.com/vlasovskikh/intellij-micropython/issues/170))
* Updated type hints for `pyb`, `machine`, `micropython`, `uarray`, `btree`, `framebuf`
  (Thanks to [@hlovatt](https://github.com/hlovatt))

### Fixed

* Fixed handling backslash while flashing files on Windows
  ([#128](https://github.com/vlasovskikh/intellij-micropython/issues/128))
  (Thanks to [@jdjjm](https://github.com/jdjjm))

1.1.4 — 2021-03-05
------------------

* Compatibility with 2020.2-2021.1

1.1.3 — 2020-12-12
------------------

* Added an SVG icon for MicroPython actions
* Fixed a regression in 2020.3 that prevented the plugin from updating and persisting
  your device type on changing it in the settings

1.1.2 — 2020-10-24
------------------

* Added a type hinting stub for `pyb`
* Switched to newer terminal API to fix regression in PyCharm 2020.2
* Compatibility with 2020.2-2020.3

1.1.1 — 2020-06-24
------------------

* Fixed control characters in REPL on Windows
* Fixed path separators for flashing directories on Windows

1.1 — 2020-06-07
----------------

* Auto-detect a connected MicroPython device with an option to specify the device path
  manually in the IDE settings for MicroPython
* Use the nearest directory marked "Sources Root" as the root of your device while
  flashing files
* Ignore `.git/`, `.idea/` and other files starting with `.` when flashing a directory
* Launch a run configuration to flash a directory from its context menu
* Compatibility with 2020.1-2020.2

1.0.14 — 2020-01-31
-------------------

* Compatibility with 2019.3-2020.1

1.0.13 — 2019-10-02
-------------------

* Compatibility with 2019.3 only

1.0.12 — 2019-07-23
-------------------

* Compatibility with 2018.3-2019.2

1.0.11 — 2019-02-12
-------------------

* Compatibility with 2018.3-2019.1

1.0.10 — 2018-11-22
-------------------

* Compatibility with 2018.3 only

1.0.9 — 2018-09-10
------------------

* Compatibility with 2018.2-2018.3

1.0.8 — 2018-07-25
------------------

* Compatibility with 2018.2 only

1.0.7 — 2018-06-19
------------------

* Compatibility with 2017.3-2018.2

1.0.6 — 2018-02-23
------------------

* Fixed several bugs in `machine` stub

1.0.5 — 2018-02-11
------------------

* Don't delete `boot.py` when removing all files from the device #35

1.0.4 — 2018-01-18
------------------

* Fixed launching REPL on Windows #12

1.0.3 — 2018-01-16
------------------

* Fixed the outdated pyserial version in requirements #26
* Fixed the error message when getting `EACCESS` error for the device file #27

1.0.2 — 2018-01-15
------------------

* Restored compatibility with IntelliJ

1.0.1 — 2018-01-15
------------------

* Restored compatibility with 2017.2-2018.1

1.0 — 2018-01-15
----------------

* Run files and REPL for ESP8266 and Pyboard devices
* Initial code insight for ESP8266 and MicroPython standard library
* Action for detecting the device path in the MicroPython settings
* Action for removing all files from the device

0.1 — 2017-06-18
----------------

* Code insight and documentation for Micro:bit Python API
* Run Python files on Micro:bit
* Micro:bit Python REPL
