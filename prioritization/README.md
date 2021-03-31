# Under-construction

## Main directory structure
```
|-- prioritization: : the code and data for the performance-impact-based prioritization
|    |-- pre-processing: pre-processing code to prepare the data used to train LtR
|         |-- Code: pre-processing code
|              |-- 0. ProcessProfileLog.ipynb: process the profile data, which is collected by running the unit-testing
|              |-- 1.1 CalcProfileStat.ipynb: process the profile data
|              |-- 1.2 AggrProfileStat.ipynb: process the profile data
|              |-- 2.1 CalcMethodExecutionFrequency.ipynb: process the FREQM
|              |-- 2.1 CountOfInstructions.ipynb: process the NOS
|              |-- 2.1 CountOfMethodCalls.ipynb: process the NOMC
|              |-- 2.1 ProcessMetricsDataInLog.ipynb: process the metrics data (NOPS/NOPMC) collected by static analysis
|              |-- 3. PrepareLtRData.ipynb: prepare the final LtR data
|         |-- Data: data used to train LtR
|              |-- [PROJECT]FullData.csv: the final LtR data
|              |-- [PROJECT]NewProfileStatSummary.xlsx: the final profile data
|              |-- [PROJECT]_Count_Of_Instructions.txt: the final NOS
|              |-- [PROJECT]_Count_Of_Method_Calls.txt: the final NOMC
|              |-- [PROJECT]_Method_Frequency.txt: the final FREQM
|              |-- [PROJECT]_Metrics_Data.txt: the final NOPS/NOPMC
|    |-- ltr: code, data and the results of LtR
|         |-- 4.1 LearnToRankTestRankers.ipynb: test the LtR models with all metrics
|         |-- 4.2 LearnToRankBaseline.ipynb: test the direct-ranking approach
|         |-- 4.3 LearnToRankTestFeatures.ipynb: test the LtR models with FREQS
|         |-- [PROJECT]Data: the data folds for each projects
|         |-- test.data: tmp file for test data
|         |-- train.data: tmp file for train data
|         |-- RankLib-2.15.jar: used RankLib, which has fixed some bugs in orginal version
|         |-- PerfSummary[PROJECT].xlsx: summary of the performance
```