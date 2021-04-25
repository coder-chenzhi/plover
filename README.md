# PLOVER
PLOVER is short for Pre-Logging OVERhead.

## Main directory structure
```
|-- preliminary_study: the code and data for the preliminary study
|    |-- guard_addition_collector.py: the code used to collect supplementary logging guards
|    |-- raw_data: the collected raw data for the five studied Apache projects
|    |-- Summary.xlsx: the analysis result on the raw data
|-- detector: the code and data for the static analysis
|    |-- ImplementationDetails: implementation details
|    |-- core: core of our static analysis
|    |-- guards-bench-extractor: the code used to collect benchmark of guards
|    |-- log-entries-extractor: the code used to collect entry-points of static analysis
|    |-- commons: utility functions of the static analysis
|    |-- conf: configurations used to run the static analysis
|    |-- docs: results of the static analysis
|-- prioritization: : the code and data for the performance-impact-based prioritization
|    |-- pre-processing: pre-processing code to prepare the data used to train LtR
|    |-- lrt: code, data and the results of LtR
```

## How to run the detector
- install JDK and Maven
- run `mvn install` to install the detector
- if you run the detector on the stuided five projects
    - change the paths in `RunConfig.java` in `detector/commons` to the right paths, including the paths to files containning the signatures of entry-points, the paths to files containning classpaths (used by Soot), the root path to the source code of projects, the paths to files containning the signatures of user-defined guards methods (can be empty file), and the paths to files containning the signatures of user-defined logging methods (can be empty file).
- if you run the detector on new projects
    - you also need to add new configuration files to `detector/conf`. Please follow the naming convention of existing configuration files. The benchmark of guards can be extracted by running `guards-bench-extractor`, and the entry-points can be extracted by running `log-entries-extractor`.
- run `MainEntry.java` in `detector/core`

## How to run the prioritation
- install Jupyter Notebook (recommend to install anaconda to ease the management of dependencies)
- if you run the detector on the stuided five projects
    - just run the code in `ltr` sequently
- if you run the detector on new projects
    - you also need to run the code in `pre-processing` sequently