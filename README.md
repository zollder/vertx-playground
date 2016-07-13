Prerequisites:
	- docker
	- docker-compose

Run using:
	docker-compose up

or do the following:

	Build the project using:
		mvn clean package

	Start MongoDB with docker:
		docker run --name mongo -d -p 27017:27017 mongo

	Launch fat jar as follows:
		java -jar target/vertxmon-0.0.1-SNAPSHOT-fat.jar

	Or start with Docker in a host mode (Linux):
		create docker image: docker build -t vertxmon .
		run in host mode: docker run --name web -t -i -p 8080:8080 --net=host vertxmon

TODO:
	- try linking containers
	- move mongo config to an external file
	- ex: java -jar target/vertxmon-0.uto0.1-SNAPSHOT-fat.jar -conf src/main/conf/application-conf.json
	- fully automated launch
	- use mvn plugin to build docker images