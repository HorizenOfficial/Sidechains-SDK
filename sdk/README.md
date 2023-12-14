**How to add new API endpoint to Swagger**
1. Make changes to /Sidechains-SDK/sdk/src/main/resources/api/sidechainApi.yaml
2. Launch examples/utxo/simpleapp/src/main/java/com/horizen/examples/SimpleApp.java 
with CLI arguments: examples/utxo/simpleapp/src/main/resources/sc_settings.conf
3. Open http://127.0.0.1:9085/swagger in browser
4. Find needed API in the list of APIs and validate it.

**How to run dependency check**
1. Add next part to the pom.xml file:
```
      <plugin>
        <groupId>org.owasp</groupId>
        <artifactId>dependency-check-maven</artifactId>
        <version>9.0.4</version>
        <executions>
          <execution>
            <goals>
              <goal>check</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
```
2. Run `mvn clean verify`
3. Open `target/dependency-check-report.html` in browser