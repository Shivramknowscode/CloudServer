<?php

use Amp\Http\Server\HttpServer;
use Amp\Http\Server\Request;
use Amp\Http\Server\Response;
use Amp\Http\Server\Router;
use Amp\Http\Status;
use Amp\Socket\Server;
use Aws\S3\Exception\S3Exception;
use Aws\S3\S3Client;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputArgument;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Logger\ConsoleLogger;
use Symfony\Component\Console\Output\OutputInterface;
use function Amp\call;
use function Amp\Promise\wait;
use Amp\Http\Server\RequestHandler\CallableRequestHandler;
class RunCommand extends Command
{
    protected static $defaultName = "run";
    protected function configure(): void
    {
        parent::configure();
        $this->addArgument("host", InputArgument::OPTIONAL, description: "Docker host to use", default: "unix:///var/run/docker.sock");
    }
    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        return wait(call(function() use($input, $output){
            $docker = new Docker($input->getArgument("host"));
            // Ensure that minio is deployed
            yield $docker->deployMinio();
            $s3 = new S3Client([
                "version" => "latest",
                "region" => "us-east-1",
                "endpoint" => "http://s3:9000",
                "credentials" => [
                    "key" => "minioadmin",
                    "secret" => "minioadmin",
                ]
            ]);
            $currentBuckets = array_column($s3->listBuckets()->get("Buckets"), "Name");
            $buckets = ["pending1", "pending2", "pending3", "result1", "result2", "result3", "pendingoutput", "output", "jars"];
            $nodes = ["worker1", "worker2", "worker3", "input", "output"];
            // Ensure all required buckets exist
            // TODO: setup permissions here
            foreach($buckets as $bucketName) {
                if(!in_array($bucketName, $currentBuckets)) {
                    $s3->createBucket([
                        "Bucket" => $bucketName
                    ]);
                }
            }

            // Start HTTP server
            $servers = [
                Server::listen("0.0.0.0:1337"),
                Server::listen("[::]:1337"),
            ];
            $router = new Router();
            // Status page/access panel
            $router->addRoute('GET', '/', new CallableRequestHandler(function () {
                return new Response(Status::OK, ['content-type' => 'text/html'], file_get_contents(__DIR__ . "/../index.html"));
            }));
            // Deploy a jar as a node
            $router->addRoute('POST', '/node/{node}', new CallableRequestHandler(function (Request $request) use($docker, $nodes, $s3) {
                // name is in the form <java version>:<jar name>
                $name = yield $request->getBody()->buffer();
                $expl = explode(":", $name);
                $java = array_shift($expl);
                $jar = implode(":", $expl);
                $node = $request->getAttribute(Router::class)["node"];
                if(!in_array($node, $nodes)) {
                    return new Response(Status::NOT_FOUND, ['content-type' => 'text/plain'], "Node $node not found");
                }
                try {
                    $s3->headObject([
                        "Bucket" => "jars",
                        "Key" => $jar,
                    ]);
                } catch(S3Exception $e) {
                    if($e->getAwsErrorCode() === "NotFound") {
                        return new Response(Status::NOT_FOUND, ['content-type' => 'text/plain'], "Jar $jar not found");
                    }
                    return new Response(Status::INTERNAL_SERVER_ERROR,  ["content-type" => "text/plain"], get_class($e) . " in ". $e->getFile() .":".$e->getLine().": " . $e->getMessage() . PHP_EOL . $e->getTraceAsString() . PHP_EOL);
                }
                $url = $s3->createPresignedRequest($s3->getCommand("GetObject", [
                    "Bucket" => "jars",
                    "Key" => $jar,
                ]), "+10 minutes")->getUri()->__toString();
                try {
                    yield $docker->deployNode($node, $url, $java);
                } catch(Throwable $e) {
                    return new Response(Status::INTERNAL_SERVER_ERROR, ["content-type" => "text/plain"], get_class($e) . " in ". $e->getFile() .":".$e->getLine().": " . $e->getMessage() . PHP_EOL . $e->getTraceAsString() . PHP_EOL);
                }
                return new Response(Status::NO_CONTENT);
            }));
            $router->addRoute('GET', '/{name}', new CallableRequestHandler(function (Request $request) {
                $args = $request->getAttribute(Router::class);
                return new Response(Status::OK, ['content-type' => 'text/plain'], "Hello, {$args['name']}!");
            }));

            $server = new HttpServer($servers, $router, new ConsoleLogger($output));
            Amp\Loop::run(static function () use($server) {
                yield $server->start();

                // Stop the server when SIGINT is received (this is technically optional, but it is best to call Server::stop()).
                Amp\Loop::onSignal(\SIGINT, static function (string $watcherId) use ($server) {
                    Amp\Loop::cancel($watcherId);
                    yield $server->stop();
                });
            });
//            ob_start();
//            var_dump(yield $docker->info());
//            $conts = ob_get_clean();
//            $output->write($conts);
            return 0;
        }));
    }
}