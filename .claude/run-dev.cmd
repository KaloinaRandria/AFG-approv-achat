@echo off
set "JAVA_HOME=C:\Users\STG00067\.jdks\ms-21.0.9"
cd /d "C:\Users\STG00067\IdeaProjects\AFG-approv-achat"
call "C:\Users\STG00067\IdeaProjects\AFG-approv-achat\mvnw.cmd" -o spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments="--server.port=8081 --spring.jpa.properties.hibernate.search.backend.directory.root=C:/Users/STG00067/AppData/Local/Temp/claude/1bf0e93a3c5bd0fe/b9efed72-ca4b-4dd6-881d-ae64d5403e59/scratchpad/lucene-index"
