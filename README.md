
Start Mongo with Docker (Linux):
	docker run -d -p 27017:27017 mongo

Build the project using:
	mvn clean package

Once packaged, just launch the fat jar as follows:
	java -jar target/vertxmon-0.0.1-SNAPSHOT-fat.jar

TODO:
	move mongo config to an external file
	ex: java -jar target/vertxmon-0.0.1-SNAPSHOT-fat.jar -conf src/main/conf/application-conf.json