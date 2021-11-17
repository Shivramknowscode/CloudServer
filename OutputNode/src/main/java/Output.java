import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Item;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class Output {

    static  String NodeID="";
    static  String ENDPOINT = "";
    static  String ACCESSKEY="";
    static  String SECRETKEY="";
    static Map<String,String> results=new HashMap<>();
    static  boolean found=true;
    static boolean consensus =false;

    public static void main(String[] args)
    {
        if(args.length!=4)
        {
            System.out.println( "Invalid Arguments. Please enter arguments in the format <NODEID> <ENDPOINT> <ACCESSKEY> <SECRETKEY>");
            System.exit(0);
        }
        NodeID=args[0];
        ENDPOINT=args[1];
        ACCESSKEY=args[2];
        SECRETKEY=args[3];

O
        boolean pendingOperation=false;
        String currentPendingOperation="";
        try {
            MinioClient minioClient =
                    MinioClient.builder()
                            .endpoint(ENDPOINT)
                            .credentials(ACCESSKEY, SECRETKEY)
                            .build();
            while(true)
            {


                Iterable<Result<Item>> listObjects = minioClient.listObjects(ListObjectsArgs.builder().bucket("pendingoutput").build());

                for (Result<Item> n : listObjects) {
                    currentPendingOperation = n.get().objectName();
                    pendingOperation=true;
                    System.out.println("Current Pending Operation: " + currentPendingOperation);
                    break;
                }
                if(pendingOperation)
                {
                    while (!consensus) {
                        retrieveResults(minioClient, currentPendingOperation);
                        int nodeID = compareResults();
                        if (consensus) {
                            System.out.println("Consensus Found between Node results.");
                            System.out.println("Copying result to output.....");
                            minioClient.copyObject(CopyObjectArgs.builder()
                                    .bucket("output")
                                    .object(currentPendingOperation)
                                    .source(CopySource.builder()
                                            .bucket("result" + nodeID)
                                            .object(currentPendingOperation)
                                            .build())
                                    .build());
                            deleteCompletedOperation(minioClient,currentPendingOperation);
                        }
                    }
                    consensus = false;
                    pendingOperation=false;
                }
                else
                {
                    System.out.println("No Pending Operation");
                }
                Thread.sleep(5000);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    public static void retrieveResults(MinioClient minioClient, String currentPendingOperation) throws InterruptedException {
        String latestResult="";


        for (int i = 1; i <= 3; i++) {
            Thread.sleep(2000);
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket("result"+i)
                            .object(currentPendingOperation)
                            .build())) {
                InputStreamReader inputStreamReader=new InputStreamReader(stream);
                BufferedReader bufferedReader=new BufferedReader(inputStreamReader);
                String jsonRPC=bufferedReader.readLine();
                Object obj = new JSONParser().parse(jsonRPC);
                JSONObject jsonObject = (JSONObject) obj;
                latestResult=jsonObject.get("result").toString();

            } catch (Exception e) {
                if (e instanceof ErrorResponseException responseException) {
                    System.out.println( "Node"+i+" Result still pending for "+responseException.errorResponse().objectName());
                    found = false;
                }

            }
            if (found) {
                System.out.println("Node "+i+" Operation Completed. Result: "+latestResult);

                if(!results.containsKey("Node"+i))
                {
                    results.put("Node"+i,latestResult);
                }
            }
            found=true;
        }

    }

    public static int compareResults()
    {
        int nodeID=0;
        if(results.size()>=2)
        {
            if(results.containsKey("Node1")&&results.containsKey("Node2")) {
                if (results.get("Node1").equals(results.get("Node2"))) {
                    consensus = true;
                    nodeID = 1;
                    return nodeID;

                }
            }
            if(results.containsKey("Node2")&&results.containsKey("Node3")) {
                if (results.get("Node2").equals(results.get("Node3"))) {
                    consensus = true;
                    nodeID = 2;
                    return nodeID;
                }
            }

            if(results.containsKey("Node1")&&results.containsKey("Node3")) {
                if (results.get("Node1").equals(results.get("Node3"))) {
                    consensus = true;
                    nodeID = 3;
                    return nodeID;
                }
            }
        }
        return nodeID;
    }

    public static void deleteCompletedOperation(MinioClient minioClient, String completedOperation) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {

        for (int i = 1; i <= 3; i++) {
            System.out.println("Removing "+completedOperation+" from Node"+i);
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket("result"+i)
                    .object(completedOperation)
                    .build());
        }
        System.out.println("Removing "+completedOperation+" from PendingOutput");
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket("pendingoutput")
                .object(completedOperation)
                .build());
        results.clear();

    }



    public static void makeBuckets(MinioClient minioClient)
    {
        try {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("pending1").build());
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("pending2").build());
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("pending3").build());
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("result1").build());
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("result2").build());
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("result3").build());
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("pendingoutput").build());
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("output").build());
        }catch (Exception e)
        {
            e.printStackTrace();
        }
    }

}
