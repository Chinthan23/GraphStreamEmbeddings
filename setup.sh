#!/bin/bash
#-- Dependencies
    #-sbt
    # install using `sudo apt install sbt` or `brew install sbt` on mac
    # virtualenv: pip install virtualenv
    
mkdir -p producer && cd producer > /dev/null
mv ../generator.py .
mv ../facebook-links.txt .
python3 -m venv prod_env
source prod_env/bin/activate
pip install numpy
pip install pandas
pip install faker
pip install kafka-python
# update link if link is not working
wget https://archive.apache.org/dist/kafka/3.2.1/kafka_2.13-3.2.1.tgz -q --show-progress
echo "Downloaded kafka"
tar -xvzf kafka_2.13-3.2.1.tgz &> /dev/null
rm kafka_2.13-3.2.1.tgz
your_ip='localhost'
advertised_listeners="advertised.listeners=PLAINTEXT://${your_ip}:9092"
sed -i "" "s%^#advertised.*%${advertised_listeners}%" ./kafka_2.13-3.2.1/config/server.properties
git clone https://github.com/yahoo/CMAK.git
cd CMAK
sbt clean dist
#./sbt clean dist
cd target/universal
unzip *.zip
cd cmak-3.0.0.7
zookeeper_host="cmak.zkhosts=\"localhost:2181\""
cd conf
sed -i "" "s%^cmak.zkhosts=\"kafka-manager-zookeeper:2181\"*%${zookeeper_host}%" ./application.conf
