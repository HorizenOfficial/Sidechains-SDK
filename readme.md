
1. Clone Scorex git repository:
   git clone -n https://github.com/ScorexFoundation/Scorex.git Scorex
   cd Scorex
   git git checkout 6ffeafc

2. Comment out lines 22-33 and 153-169 in file build.sbt

3. Publish Scorex core to local maven repository:
   sbt publishM2

4. Change directory to Sidechain-SDK

5. Publish Sidechain-SDK to local maven repository:
   mvn install

6. Change directory to examples/simpleapp

7. Build SimpleApp:
   mvn package