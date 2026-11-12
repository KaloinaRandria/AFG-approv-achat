@echo off
set "JAVA_HOME=C:\Users\STG00067\.jdks\ms-21.0.9"
cd /d "C:\Users\STG00067\IdeaProjects\AFG-approv-achat"
call "C:\Users\STG00067\IdeaProjects\AFG-approv-achat\mvnw.cmd" -o spring-boot:run -Dspring-boot.run.profiles=dev
