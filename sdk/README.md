**Add new API endpoint to Swagger**
1. Make changes to /home/sergiy/IdeaProjects/Sidechains-SDK/sdk/src/main/resources/api/sidechainApi.yaml
2. Launch examples/simpleapp/src/main/java/com/horizen/examples/SimpleApp.java 
with CLI arguments: examples/simpleapp/src/main/resources/sc_settings.conf
and working directory: /home/sergiy/IdeaProjects/Sidechains-SDK
3. Add browser and go to http://127.0.0.1:9085/swagger
4. Find needed API in the list of APIs and validate it.