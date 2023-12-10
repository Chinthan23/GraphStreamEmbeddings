import json
import pandas as pd
import numpy as np
from kafka import KafkaProducer
import time

# Setting up Kafka
def json_serializer(data):
    return json.dumps(data).encode('utf-8')

producer = KafkaProducer(bootstrap_servers=['localhost:9092'], value_serializer=json_serializer)

def data_frame_to_send(file_path):
	# Reading the edge list from required file
	data=pd.read_csv(file_path,delimiter="\t",header=None)
	# Defining the columns as per the dataset
	data.columns=["src","dst","timestamp"]
	# Replacing appropriately for null values
	data["timestamp"].replace(to_replace="\\N",value=np.nan,inplace=True)
	data["timestamp"]=np.float64(data["timestamp"])
	return data

if __name__=="__main__":
    data=data_frame_to_send("./facebook-links.txt")
    # print(data.dtypes)
    print("Enter the throughput of the producer: ",end="  ")
    throughput=int(input()) # Giving control over the throughput to the user
    i=0
    data_to_send={}
    while i<data.shape[0]:
        start_time=time.time()
        for _ in range(throughput):
            data_to_send["src"]=int(data.iloc[i,0])
            data_to_send["dst"]=int(data.iloc[i,1])
            timestamp=data.iloc[i,2]
            data_to_send["timestamp"]=None if pd.isna(timestamp) else timestamp
            print(data_to_send)
            producer.send("graphs",data_to_send)
            i+=1
        time.sleep(1-(time.time()-start_time)) # Sleeping for the rest of a second
            