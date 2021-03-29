# Hadoop

## Build


### Download source code
```sh
wget https://archive.apache.org/dist/hadoop/core/hadoop-2.9.2/hadoop-2.9.2-src.tar.gz
```
### Unzip
```sh
tar zxvf hadoop-2.9.2-src.tar.gz && cd hadoop-2.9.2-src
```

### Patch
To run OpenClover on hadoop, we need to edit some config files of hadoop,
- Change Clover to open-source version
- Fix unnecessary steps in docker file

For convenience, we have provided patch file 
```sh
git init
git apply ${YOUR_PATCH_DIR}/Hadoop2.9.2_OpenClover.patch
```

### Run Docker
```sh
./start-build-env.sh
```

### Run Open Clover
```sh
mvn test -Pclover -Dmaven.test.failure.ignore=true
### We need to ignore failed tests, because there are some flakey tests usually fail to run.
```

### MISC
The unit testing of HDFS always hangs in my server. The header of the error log of jvm like following:

```
# A fatal error has been detected by the Java Runtime Environment:
#
#  Internal Error (safepoint.cpp:310), pid=4411, tid=0x0000000000003503
#  guarantee(PageArmed == 0) failed: invariant
``` 
After some search, I realize that I need to upgrade the default JDK from 7 to 8. We can archive this by following the instructions in https://gist.github.com/starlinq/9ee5209ceb32b7e05817e714fe530be3. Unfortuantely, the default user of Hadoop container do not have root permissions. Therefore, we first need to enter the container with root `sudo docker run -it -u root [container_id]`. After the upgrade, we need to enter the container with your default user again, so that you can resue the content under `.m2` in your host.  

# Cassandra (3.11.3)
## Prerequisites
- Open JDK: `sudo apt-get install openjdk-8-jdk`
- Apache Ant: `sudo apt install ant`

## Build
Cassandra use Cobertura to generate test coverage report before 2014 and turn to JaCoCo since CASSANDRA-7226. Support of Cobertura is removed since CASSANDRA-10704.

### Download source code
```sh
wget https://archive.apache.org/dist/cassandra/3.11.3/apache-cassandra-3.11.3-src.tar.gz
```
### Unzip
```sh
tar zxvf apache-cassandra-3.11.3-src.tar.gz && cd apache-cassandra-3.11.3-src
```
### Fix http issue of maven
Change all http to https in file `build.properties.default`

Change `http://repo2.maven.org` tp `https://repo.maven.apache.org` in `build.xml`

### Run JaCoCo
```sh
ant codecoverage
```

**JaCoCo can also be run step-by-step:**
```sh
ant jacoco-run -Dusejacoco=yes -Dtaskname=test
ant jacoco-report
```
However, JaCoCo neither maintain the execution frequency for covered blocks and methods, 
nor maintain which blocks are covered by which test cases. For a workaround, 
we can run and collect metrics for test cases one by one, but it is time-consuming.

# Zookeeper (3.4.13)

## Prerequisites
- Open JDK: `sudo apt-get install openjdk-8-jdk`
- Apache Ant: `sudo apt install ant`

## Build

### Download source code
```sh
wget https://archive.apache.org/dist/zookeeper/zookeeper-3.4.13/zookeeper-3.4.13.tar.gz
```
### Unzip
```sh
tar zxvf zookeeper-3.4.13.tar.gz && cd zookeeper-3.4.13
```
### Run Test Coverage

NOTE!!! Run `ant clean` to clear ./build/classes and ./build/test/classes before running clover.
Otherwise, Ant will not regenerate these class files, and Clover will not instrumented them.

NOTE!!! Each test case has its own log file to store log messages during testing. 
If you want to output log messages into one log files, remember to enable FileAppender in ./conf/log4j.properties.
You can change `log4j.rootLogger` to `INFO, CONSOLE, TRACEFILE`

To generate Java code coverage report run:
```sh
ant test-coverage-clover-java
```

org.apache.zookeeper.server.ZooKeeperServerMainTest often fail to run in Vultr,
we need to run clover step-by-step like following

**Clover can also be run step-by-step:**
```sh
ant -Drun.clover=true test-core-java
ant clover-report
```

For quick testing for specific test cases run:
```sh
ant -Dtestcase=test_file_name test-coverage-clover-java
```




