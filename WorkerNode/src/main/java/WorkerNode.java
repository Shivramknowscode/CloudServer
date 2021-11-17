import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Item;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.imageio.IIOException;
import java.io.*;
import java.util.Locale;

/*
Worker Node is responsible for selecting a work request entry from its respective PendingN S3 bucket
and performing the requested operation. After completing the requested operation the worker node will
add the result to the corresponding resultN bucket.
 */
public class WorkerNode {

    static String nodeIdStr = "";

    public static void main(String[] args){
        String workerName = args[0];
        nodeIdStr = workerName.substring(workerName.length()-1);
        System.out.println("NodeID: "+nodeIdStr);
        String ENDPOINT = args[1];
        String ACCESSKEY = args[2];
        String SECRETKEY = args[3];
        String currentPendingOperation = "";

        try {
            MinioClient minioClient = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESSKEY, SECRETKEY).build();
            while(true){
                Thread.sleep(5000);

                boolean pendingOperation = false;

                //Check if object(file) exists in bucket pending<nodeId>
                Iterable<Result<Item>> listObjects = minioClient.listObjects(ListObjectsArgs.builder().bucket("pending"+nodeIdStr).build());
                for (Result<Item> n: listObjects) {
                    currentPendingOperation = n.get().objectName();
                    pendingOperation = true;
                    System.out.println("Current Pending Operation: " + currentPendingOperation);
                    break;
                }
                //If new object(file) exists, read operation request from file (json rpc format)
                if(pendingOperation) {
                    processOperation(minioClient, currentPendingOperation);

                    //Delete object(file) from pending<nodeId>
                    System.out.println("Removing "+currentPendingOperation+" from pending"+nodeIdStr);
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket("pending"+nodeIdStr)
                            .object(currentPendingOperation)
                            .build());
                } else {
                    System.out.println("No Pending Operation");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void processOperation(MinioClient minioClient, String currentPendingOperation) {
        String latestPendingOperation="";
        String operationMethod = "";
        String operationInputString = "";
        Boolean found = true;

        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                .bucket("pending"+nodeIdStr)
                .object(currentPendingOperation)
                .build())) {
            InputStreamReader inputStreamReader=new InputStreamReader(stream);
            BufferedReader bufferedReader=new BufferedReader(inputStreamReader);
            String jsonRPC=bufferedReader.readLine();
            Object obj = new JSONParser().parse(jsonRPC);
            JSONObject jsonObject = (JSONObject) obj;
            operationMethod=jsonObject.get("method").toString();
            operationInputString=jsonObject.get("params").toString();

            determineOperationType(minioClient, operationMethod, operationInputString, currentPendingOperation);
            System.out.println("Operation successfully completed");
        } catch (Exception e) {
            if (e instanceof ErrorResponseException ) {
                ErrorResponseException responseException = (ErrorResponseException)e;
                System.out.println( responseException.errorResponse() );
                found = false;
            }
        }
        if(found) {
            System.out.println();
        }
    }

    public static void determineOperationType(MinioClient minioClient, String operationMethod, String operationInputString, String currentPendingOperation) {
        String operationResult = "";
        switch (operationMethod){
            case ("reverseString"):
                operationResult = reverseString(operationInputString);
                writeResult(minioClient, operationResult, currentPendingOperation);
                break;
            case ("toUpperCase"):
                operationResult = toUpperCase(operationInputString);
                writeResult(minioClient, operationResult, currentPendingOperation);
            default:
                System.out.println("Operation Method was not defined");
        }

    }

    public static void writeResult(MinioClient minioClient, String operationResult, String currentPendingOperation) {
        //Example result written to resultN bucket: {"jsonrpc": "2.0","result": "test", "id": 2}
        JSONObject obj = new JSONObject();
        obj.put("jsonrpc", "2.0");
        obj.put("result", operationResult);
        String[] pendingOperationParts = currentPendingOperation.split("\\.");
        obj.put("id", pendingOperationParts[0]);
        System.out.print(obj);

        StringBuilder builder = new StringBuilder(obj.toString());
        // Create a InputStream for object upload.
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(builder.toString().getBytes("UTF-8"));

            // Create object currentPendingOperation in 'result'+nodeIdStr with content from the input stream.
            //Store result in result<nodeId> bucket with filename equal to filename of object(file) read from pending<nodeId>
            minioClient.putObject(
                    PutObjectArgs.builder().bucket("result"+nodeIdStr).object(currentPendingOperation).stream(
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

    }

    public static String reverseString(String inputString) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(inputString);
        return stringBuilder.reverse().toString();
    }

    public static String toUpperCase(String inputString) {
        return inputString.toUpperCase();
    }
}
