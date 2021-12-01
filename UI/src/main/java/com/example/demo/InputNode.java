package com.example.demo;

import io.minio.*;
import io.minio.errors.*;
import org.json.simple.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.imageio.IIOException;
import java.io.*;
import java.io.FileWriter;
import java.util.UUID;

import static com.example.demo.MainApplication.nodeIdStr;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InputNode {

    private String userOperation;
    private String userInput;

    public String getUserOperation(){
        return this.userOperation;
    }

    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }

    public String getUserInput(){
        return this.userInput;
    }

    public void setUserOperation(String userOperation) {
        this.userOperation = userOperation;
    }

    public static String inputSelection(MinioClient minioClient, String userOperation, String userInput, String currentPendingOperation) throws IOException {
        //Example result written to resultN bucket: {"jsonrpc": "2.0","result": "test", "id": 2}
        JSONObject obj = new JSONObject();
        obj.put("userOperation", userOperation);
        obj.put("userInput", userInput);
        String[] pendingOperationParts = currentPendingOperation.split("\\.");
        obj.put("id", pendingOperationParts[0]);
        System.out.print(obj);


        // Constructs a FileWriter given a file name, using the platform's default charset to view data on a text editor
        FileWriter file = new FileWriter("/Users/Shivram/Documents/crunchify.txt");
        file.write(obj.toJSONString());
        System.out.println("Successfully Copied JSON Object to File...");
        System.out.println("\nJSON Object: " + obj);


        StringBuilder builder = new StringBuilder(obj.toString());
        // Create a InputStream for object upload.
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(builder.toString().getBytes("UTF-8"));

            // Create object currentPendingOperation in 'result'+nodeIdStr with content from the input stream.
            //Store result in result<nodeId> bucket with filename equal to filename of object(file) read from pending<nodeId>
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(nodeIdStr).object(currentPendingOperation).stream(
                                    bais, bais.available(), -1)
                            .build());

            bais.close();
        } catch (Exception e) {
            if (e instanceof ErrorResponseException ) {
                ErrorResponseException responseException = (ErrorResponseException)e;
                System.out.println( responseException.errorResponse() );
            } else if(e instanceof UnsupportedEncodingException){
                UnsupportedEncodingException unsupportedEncodingException = (UnsupportedEncodingException)e;
                System.out.println(unsupportedEncodingException.getMessage());
            } else if(e instanceof IIOException) {
                IOException ioException = (IOException)e;
                System.out.println(ioException.getMessage());
            }
        }
        System.out.println("Operation results uploaded successfully");

        return userOperation;
    }

    public static void main(String[] args) throws IOException {

        SpringApplication.run(InputNode.class, args);
        String ENDPOINT = "http://127.0.0.1:9000";
        String ACCESSKEY = "minioadmin";
        String SECRETKEY = "minioadmin";
        String currentPendingOperation = UUID.randomUUID().toString();

        MinioClient minioClient = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESSKEY, SECRETKEY).build();
        inputSelection(minioClient,currentPendingOperation,"Test","pending");

    }


}
