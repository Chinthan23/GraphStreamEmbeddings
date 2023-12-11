# GraphStream

# About

The scripts within this repository allow one to set up a Kafka producer, that will generate streaming data for your streaming applications. The repository also contains an implementation of Node2vec for a streaming graph taken from https://github.com/aditya-grover/node2vec.git. The code is modified to work for a streaming graph instead of a complete graph.


## Dependencies
openjdk 11
sbt
virtualenv

# Setup

The setup script downloads, Kafka, Zookeeper, and Cluster Manager for Kafka and configures them for a local deployment. The run script would run the above programs, and keep them running in the background.

The Cluster Manager for Apache Kafka(CMAK) provides a graphical interface to create clusters, add topics and partitions etc.

Run the script as follows:

`./setup.sh`

# Running the producer

### Starting Kafka, Zookeeper and CMAK

Once the setup is done, start the processes by executing the *[start.sh](http://start.sh)* in separate terminal instances and in order as shown below:

`./start.sh START_ZOOKEEPER` 

`./start.sh START_KAFKA` 

`./start.sh START_CMAK` 

This runs the Kafka, Zookeeper and CMAK in the background.

## Creating a cluster and adding a topic

Creating a cluster and adding topics can be done using the GUI provided by CMAK, without using any scripts. The web interface is available at `[localhost:8080](http://localhost:8080)` .

# Spark program

Open the GraphStream folder within the repository in IntelliJ Idea and run the following commands through maven:

```jsx
mvn clean install
```

Build and run the project to start the Spark program in intelliJ.

# Outputs
Some of the outputs obtained are shown in the directories named as embeddings. Embeddings themselves can be found in the output..txt files.

# Future Scope
The work done here was part of a course Project, Streaming Data Systems by Prof. Vinu E V at IIITB. 

This can be built upon to improve the overall performance to get graph embeddings more accurately using better models which work on batched data and can update embeddings as and when the stream proceeds in time. 

Also partitioning algorithms can be implemented to ensure better graph embeddings (though at time of this project, I was unable to find a way to do so except using the repartition function).
## References

- [https://sparkbyexamples.com/spark/spark-setup-run-with-scala-intellij/](https://sparkbyexamples.com/spark/spark-setup-run-with-scala-intellij/)
