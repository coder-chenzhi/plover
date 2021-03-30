# Detector

## Main directory structure
```
|-- detector: the code and data for the static analysis
|    |-- core: core of our static analysis
|    |-- guards-bench-extractor: the code used to collect benchmark of guards
|    |-- log-entries-extractor: the code used to collect entry-points of static analysis
|    |-- commons: utility functions of the static analysis
|    |-- conf: configurations used to run the static analysis
|         |-- classpaths: classpath for Soot, which define the analysis scope
|         |-- guard-bench: collected benchmark of guards
|         |-- guard-methods: signatures of guards
|         |-- log-entries: signatures of entry-points
|         |-- logging-methods: signatures of user-defined logging methods
|    |-- docs: results of the static analysis
|         |-- coverage: instructions for running the unit-testing of the studied projects
|         |-- resuls: results of the static analysis
|         |-- resuls: results of the static analysis
|              |-- [PROJECT]_Data_Control_SideEffect_Raw_Result.txt: result of DataDep + CtrlDep + Side-effect
|              |-- [PROJECT]_Data_Control_SideEffect_Bench_Comapre_Result.txt: recall of DataDep + CtrlDep + Side-effect
|              |-- [PROJECT]_Data_Control_NoSideEffect_Raw_Result.txt: detection result of DataDep + CtrlDep
|              |-- [PROJECT]_Data_Control_NoSideEffect_Bench_Comapre_Result.txt: recall of DataDep + CtrlDep
|              |-- [PROJECT]_Data_NoControl_SideEffect_Raw_Result.txt: result of DataDep + Side-effect
|              |-- [PROJECT]_Data_NoControl_SideEffect_Bench_Comapre_Result.txt: recall of DataDep + Side-effect
|              |-- [PROJECT]_Data_NoControl_NoSideEffect_Raw_Result.txt: result of DataDep (Baseline)
|              |-- [PROJECT]_Data_NoControl_NoSideEffect_Bench_Comapre_Result.txt: recall of DataDep (Baseline)
```