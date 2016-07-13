###
# Deploys a Java verticle inside Docker. The verticle is packaged as a fat jar.
# To build: docker build -t vertxmon .
# To run: docker run -t -i -p 8080:8080 web
###

FROM java:8-jre

ENV VERTICLE_FILE vertxmon-0.0.1-SNAPSHOT-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

# Copy your fat jar to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["java -jar $VERTICLE_FILE"]