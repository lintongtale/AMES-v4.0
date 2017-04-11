# Install Instructions for AMES (V4.0)

### Install Java

Java can be downloaded from here [https://java.com/en/download/](https://java.com/en/download/).

### Install Python

**Option 1 : Using Anaconda (RECOMMENDED)**

_Note: if you have previously installed Anaconda use `conda update anaconda` from the command line_

1. Download the latest version of Anaconda2 x64 from Continuum <https://www.continuum.io/downloads>. You don't have to enter any contact information if you don't want to.
    * Windows
        * Click on Anaconda2-4.0.0-Windows-x86_64.exe and follow instructions

    * OSX
        * Click on Anaconda2-4.0.0-MacOSX-x86_64.dmg and follow instructions

2. Verify that conda has been installed
    * `conda --version` should result in `conda 4.0.X`
    * Windows : pip --version should result in something like `pip 8.1.X from C:\Anaconda2\lib\site-packages (python 2.7)`
    * OSX : `pip --version` should result in something like `pip 8.1.X from /Users/$USER/anaconda/lib/python2.7/site-packages (python 2.7)`


Do not proceed if you do not see `anaconda` in the path when you type `pip --version`. If you do not see anaconda, reinstall anaconda and check if the installation was successful. If you still do not see anaconda in the path, check your `$PATH` variable to see if the directory to anaconda exists.

**Option 2 : Using (System) Python (Advanced*)**

1. Download the latest version of Python2 from here <https://www.python.org/downloads/> i.e. Python 2.7.11
2. Follow instructions to install
3. Note that this is standard Python, and does not come with binaries for matplotlib, numpy, scipy, pandas etc. If you know how to install these then go ahead. Check advanced instructions for more information

### Install Pyomo

```
pip install pyomo
```

### Install CPLEX

CPLEX can be downloaded from [https://www-01.ibm.com/software/commerce/optimization/cplex-optimizer/](https://www-01.ibm.com/software/commerce/optimization/cplex-optimizer/)


