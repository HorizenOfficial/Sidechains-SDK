# New version build functionality

As of 2022/06/27 CI/CD pipeline adds functionality to build and publish multiple `DIGIT.DIGIT.DIGIT-SNAPSHOT` versions of `zendoo-sc-crypotolib` package
with the help of set_version.sh script.

`set_version.sh` script is located under **ci/devtools** directory and automates preparation steps for building/releasing a new
version of the artifacts by setting the provided version for all the required dependencies across the configuration files.

---
## Prerequisites for publishing a package:
  - Signed by GPG key commit and valid GitHub tag in the format of `DIGIT.DIGIT.DIGIT` or `DIGIT.DIGIT.DIGIT-SNAPSHOT`
  - GitHub tag matching `${pom_version_of_package}"[0-9]*$` regex
  - Your (a person who pushes a tag) GPG key being added to CI/CD pipeline build settings

Otherwise, the build process will run without entering the publishing stage.

`DIGIT.DIGIT.DIGIT-SNAPSHOT` package version can be built multiple times by adjusting GitHub tag name accordingly. For example:
```
GitHub tag = 1.1.1-SNAPSHOT can build 1.1.1-SNAPSHOT package
GitHub tag = 1.1.1-SNAPSHOT1 can build 1.1.1-SNAPSHOT package
GitHub tag = 1.1.1-SNAPSHOT2 can build 1.1.1-SNAPSHOT package
```
All SNAPSHOT packages are being pushed to the same repository as release packages and can be referred to inside the configuration
files by providing the full version, that can be found inside Central [repository](https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/io/horizen/sidechains-sdk/)

---
## Usage
Before starting the build process use `set_version.sh` script if needed by providing two arguments in the following format:
```
 ./ci/devtools/set_version.sh --help
  Usage: Provide OLD and NEW versions as the 1st and 2nd arguments respectively.
         It has to match the following format:
         DIGIT.DIGIT.DIGIT or DIGIT.DIGIT.DIGIT-SNAPSHOT

         For example:
         ./set_version.sh 5.5.5 5.5.5-SNAPSHOT
         ./set_version.sh 5.5.5-SNAPSHOT 5.5.5
```
| Changes made by set_version.sh script need to be committed before the build. |
|------------------------------------------------------------------------------|

---
## How to refer
- Find all the existing versions of [0.3.5-SNAPSHOT package](https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/io/horizen/sidechains-sdk/0.3.5-SNAPSHOT/)
- Use the full version of SNAPSHOT package as a dependency in the following format for your project.
```
<dependency>
    <groupId>io.horizen</groupId>
    <artifactId>sidechains-sdk</artifactId>
    <version>0.3.5-20220609.200517-2</version>
</dependency>
```