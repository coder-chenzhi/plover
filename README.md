# PLOVER
PLOVER is short for Pre-Logging OVERhead.

## Main directory structure
```
|-- preliminary_study: the code and data for the preliminary study
|    |-- guard_addition_collector.py: the code used to collect supplementary logging guards
|    |-- raw_data: the collected raw data for the five studied Apache projects
|    |-- Summary.xlsx: the analysis result on the raw data
|-- detector: the code and data for the static analysis
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


## How to run the prioritation