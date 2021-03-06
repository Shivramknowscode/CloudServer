package com.example.demo;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MainApplication {

    
    static String nodeIdStr = "pendingoutput";

    public static void main(String[] args){

        SpringApplication.run(InputNode.class, args);
        String randUUID = "";
        randUUID = randUUID.toString();

        InputNode inputData = new InputNode();
// hyphens or any special chars
// pendingoutput
        String workerName = "pendingoutput";
        //nodeIdStr = workerName.substring(workerName.length()-1);
        nodeIdStr = workerName;
        System.out.println("NodeID: "+nodeIdStr);
        String ENDPOINT = "http://127.0.0.1:9000";
        String ACCESSKEY = "minioadmin";
        String SECRETKEY = "minioadmin";
        String currentPendingOperation = randUUID;

        try {
            MinioClient minioClient = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESSKEY, SECRETKEY).build();
            while(true){
                Thread.sleep(5000);

                    boolean pendingOperation = false;

                //Check if object(file) exists in bucket pending<nodeId>
                Iterable<Result<Item>> listObjects = minioClient.listObjects(ListObjectsArgs.builder().bucket(nodeIdStr).build());
                for (Result<Item> n: listObjects) {
                    currentPendingOperation = n.get().objectName();
                    pendingOperation = true;
                    System.out.println("Current Pending Operation: " + currentPendingOperation);
                    break;
                }
                //If new object(file) exists, read operation request from file (json rpc format)
//                if(pendingOperation) {
//                    processOperation(minioClient, currentPendingOperation);
//
//                    //Delete object(file) from pending<nodeId>
//                    System.out.println("Removing "+currentPendingOperation+" from pending"+nodeIdStr);
//                    minioClient.removeObject(RemoveObjectArgs.builder()
//                            .bucket("pending"+nodeIdStr)
//                            .object(currentPendingOperation)
//                            .build());
//                } else {
//                    System.out.println("No Pending Operation");
//                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        SpringApplication.run(MainApplication.class, args);

    }
}
