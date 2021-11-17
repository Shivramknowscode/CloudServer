<?php

use Amp\Http\Client\Connection\DefaultConnectionFactory;
use Amp\Http\Client\Connection\UnlimitedConnectionPool;
use Amp\Http\Client\HttpClient;
use Amp\Http\Client\HttpClientBuilder;
use Amp\Http\Client\Request;
use Amp\Http\Client\Response;
use Amp\Promise;
use Amp\Socket\DnsConnector;
use Amp\Socket\StaticConnector;
use function Amp\call;

class Docker
{
    private HttpClient $client;
    public function __construct(string $host)
    {
        // Unix sockets require a socket pool that changes all URLs to a fixed one.
        $connector = new StaticConnector($host, new DnsConnector);

        $this->client = (new HttpClientBuilder)
            ->usingPool(new UnlimitedConnectionPool(new DefaultConnectionFactory($connector)))
            ->build();
    }

    public function deployMinio(int $attemptNum = 0): Promise
    {
        return call(function() use($attemptNum){
            // Search for patchability network
            $getNetworkResult = yield $this->client->request(new Request("http://localhost/networks/patchability"));
            if($getNetworkResult->getStatus() === 404) {
                // Create network
                $createNetworkRequest = new Request("http://localhost/networks/create", "POST");
                $createNetworkRequest->setBody(json_encode([
                    "Name" => "patchability"
                ], JSON_THROW_ON_ERROR));
                $createNetworkRequest->setHeader("Content-Type", "application/json");
                $createNetworkResult = yield $this->client->request($createNetworkRequest);
                if($createNetworkResult->getStatus() !== 201) {
                    throw new RuntimeException("Error creating network: " . $createNetworkResult->getStatus() . " " . (yield $createNetworkResult->getBody()->buffer()));
                }
            }

            // Attach self to patchability network
            $attachSelfRequest = new Request("http://localhost/networks/patchability/connect", "POST");
            $attachSelfRequest->setBody(json_encode([
                "Container" => gethostname()
            ], JSON_THROW_ON_ERROR));
            $attachSelfRequest->setHeader("Content-Type", "application/json");
            $attachSelfResponse = yield $this->client->request($attachSelfRequest);
            if($attachSelfResponse->getStatus() !== 200) {
                throw new RuntimeException("Error attaching self (".gethostname().") to network");
            }


            // Search for running minio container
            /**
             * @var Response $getMinioContainerResult
             */
            $getMinioContainerResult = yield $this->client->request(new Request("http://localhost/containers/s3/json"));
            if($getMinioContainerResult->getStatus() === 200) {
                $minioContainerResult = json_decode(yield $getMinioContainerResult->getBody()->buffer(), JSON_THROW_ON_ERROR);
                // TODO if a newer version of minio exists, kill it and continue
            } else {
                // Pull minio image
                $pullMinioImageRequest = new Request("http://localhost/images/create?fromImage=minio/minio&tag=latest", "POST");
                /**
                 * @var Response $pullMinioImageResult
                 */
                $pullMinioImageResult = yield $this->client->request($pullMinioImageRequest);
                if ($pullMinioImageResult->getStatus() !== 200) {
                    throw new RuntimeException("Error downloading minio");
                }
                yield $pullMinioImageResult->getBody()->buffer();


                // Create minio container
                $req = new Request("http://localhost/containers/create?name=s3", "POST");
                $req->setBody(json_encode([
                    "Image" => "minio/minio",
                    "Cmd" => ["server", "/data", "--console-address", ":9001"],
                    "Healthcheck" => [
                        "Test" => ["CMD", "curl", "127.0.0.1:9000"],
                        "Interval" => 100_000_000,
                        "Timeout" => 100_000_000,
                        "Retries" => 600,
                        "StartPeriod" => 100_000_000
                    ],
                    "ExposedPorts" => [
                        "9000/tcp" => new stdClass(),
                        "9001/tcp" => new stdClass(),
                    ],
                    "PortBindings" => [
                        // Deploy console to host system for debugging purposes
                        "9001/tcp" => [
                            [
                                "HostIp" => "",
                                "HostPort" => "9001",
                            ],
                        ],
                        "9000/tcp" => [
                            [
                                "HostIp" => "",
                                "HostPort" => "9002",
                            ],
                        ]
                    ]
                ], JSON_THROW_ON_ERROR));
                $req->setHeader("Content-Type", "application/json");
                
                /**
                 * @var Response $createMinioContainerResult
                 */
                $createMinioContainerResult = yield $this->client->request($req);
                if ($createMinioContainerResult->getStatus() !== 201) {
                    throw new RuntimeException("Error starting minio: " . $createMinioContainerResult->getStatus() . (yield $createMinioContainerResult->getBody()->buffer()));
                }
                // Attach to patchability network
                $attachMinioContainerRequest = new Request("http://localhost/networks/patchability/connect", "POST");
                $attachMinioContainerRequest->setBody(json_encode([
                    "Container" => "s3"
                ], JSON_THROW_ON_ERROR));
                $attachMinioContainerRequest->setHeader("Content-Type", "application/json");
                $attachMinioContainerResult = yield $this->client->request($attachMinioContainerRequest);
                if($attachMinioContainerResult->getStatus() !== 200) {
                    throw new RuntimeException("Error attaching network");
                }

                $getMinioContainerResult = yield $this->client->request(new Request("http://localhost/containers/s3/json"));
                if($getMinioContainerResult->getStatus() !== 200) {
                    throw new RuntimeException("Error starting minio: " . $getMinioContainerResult->getStatus() . (yield $getMinioContainerResult->getBody()->buffer()));
                }
                $minioContainerResult = json_decode(yield $getMinioContainerResult->getBody()->buffer(), JSON_THROW_ON_ERROR);
            }
            $status = $minioContainerResult["State"]["Status"];
            switch($status) {
                case "created":
                    // Container created, start process
                    $startContainerResult = yield $this->client->request(new Request("http://localhost/containers/s3/start", "POST"));
                    if($startContainerResult->getStatus() !== 204) {
                        throw new RuntimeException("Error starting minio");
                    }
                    break;
                case "running":
                    // Good!
                    break;
                default:
                    // Container failed - retry
                    if($attemptNum > 3) {
                        throw new RuntimeException("Error starting minio");
                    }
                    $removeContainerResult = yield $this->client->request(new Request("http://localhost/containers/s3", "DELETE"));
                    if($removeContainerResult->getStatus() !== 204) {
                        throw new RuntimeException("Error starting minio");
                    }
                    yield $this->deployMinio($attemptNum + 1);
                    break;
            }
            for($attempt = 0; $attempt < 600; $attempt++) {
                // Try to connect to S3, giving up to 60 seconds

                $getMinioContainerResult = yield $this->client->request(new Request("http://localhost/containers/s3/json"));
                if($getMinioContainerResult->getStatus() !== 200) {
                    throw new RuntimeException("Error starting minio");
                }
                $minioContainerResult = json_decode(yield $getMinioContainerResult->getBody()->buffer(), JSON_THROW_ON_ERROR);
                $healthStatus = $minioContainerResult["State"]["Health"]["Status"];
                if($healthStatus === "healthy") {
                    return;
                }
                // Wait 0.1 seconds
                usleep(100_000);
            }
            // Minio never got marked as healthy
            throw new RuntimeException("Minio failed to start");
        });
    }

    /**
     * @return Promise<void>
     */
    public function deployNode(string $node, string $url, string $java): Promise
    {
        return call(function() use($java, $node, $url){
            var_dump("Deploy $node $url $java\n");
            // Stop & kill $node if it exists
            yield $this->client->request(new Request("http://localhost/containers/$node/kill", "POST"));
            yield $this->client->request(new Request("http://localhost/containers/$node", "DELETE"));


            // Pull openjdk:$java
            $pullRequest = new Request("http://localhost/images/create?fromImage=openjdk&tag=$java", "POST");
            /**
             * @var Response $pullResult
             */
            $pullResult = yield $this->client->request($pullRequest);
            if ($pullResult->getStatus() !== 200) {
                throw new RuntimeException("Error downloading $node: pull returned " . $pullResult->getStatus() . " " . (yield $pullResult->getBody()->buffer()));
            }
            yield $pullResult->getBody()->buffer();

            // Create $node container with $url
            $startRequest = new Request("http://localhost/containers/create?name=$node", "POST");
            $startRequest->setBody(json_encode([
                "Image" => "openjdk:$java",
                "Cmd" => ["sh", "-c", "echo '$url' > url;curl '$url' -o jar.jar &> /log; java -jar jar.jar $node http://s3:9000 minioadmin minioadmin"]
            ], JSON_THROW_ON_ERROR));
            $startRequest->setHeader("Content-Type", "application/json");
            $startResult = yield $this->client->request($startRequest);
            if ($startResult->getStatus() !== 201) {
                throw new RuntimeException("Error creating $node: create returned " . $startResult->getStatus() . " " . (yield $startResult->getBody()->buffer()));
            }
            // Attach to patchability network
            $attachContainerRequest = new Request("http://localhost/networks/patchability/connect", "POST");
            $attachContainerRequest->setBody(json_encode([
                "Container" => $node
            ], JSON_THROW_ON_ERROR));
            $attachContainerRequest->setHeader("Content-Type", "application/json");
            $attachContainerResponse = yield $this->client->request($attachContainerRequest);

            if($attachContainerResponse->getStatus() !== 200) {
                throw new RuntimeException("Error connecting $node to network: connect returned " . $attachContainerResponse->getStatus() . " " . (yield $attachContainerResponse->getBody()->buffer()));

            }

            // Start $node container
            $startContainerResult = yield $this->client->request(new Request("http://localhost/containers/$node/start", "POST"));
            if($startContainerResult->getStatus() !== 204) {
                throw new RuntimeException("Error starting $node: start returned " . $startContainerResult->getStatus() . " " . (yield $startContainerResult->getBody()->buffer()));
            }
            echo "Deploy $node $url $java done\n";
        });
    }
}