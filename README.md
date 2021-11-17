
# Installation Instructions
## Install Docker
The rest of these instructions assume that you have Docker running on your system, listening on /var/run/docker.sock - follow https://docs.docker.com/engine/install/ with your distribution.

If you are on Windows, the easiest way to accomplish this is with WSL (https://aka.ms/wsl2) and installing the Docker page from there.  Alternatively, you may find success installing Docker Desktop.

## Resetting Docker
If you have a previous installation, you may find it useful to reset your Docker installation to its initial state.  Note that this will remove any saved volumes, images, or containers:
* docker ps|awk '{print $1}'|grep -v '^CONTAINER$'|xargs docker rm -f
* docker system prune -af
* docker volume prune -f

## Logging in to Docker Hub (optional)
Docker Hub has a rate limit of 100 pulls every 6 hours - if you reach the rate limit you may need to log in to a Docker Hub account via Portainer's Settings > Registries or by running `docker login`.

## Deploying Portainer (optional)
Portainer is a graphical user interface for Docker.  In order to deploy it to your system, run the following commands:
* docker volume create portainer_data
* docker run -d -p 9000:9000 --name portainer --restart=always -v /var/run/docker.sock:/var/run/docker.sock -v portainer_data:/data portainer/portainer-ce:latest

Open the Portainer console in http://127.0.0.1:9000 and set a username and password for yourself (this is only for the console, not for the application itself).  Press Get Started and select the Local environment to confirm that you are operating locally.

## Deploying Manager Node
The Manager Node takes care of deploying all other nodes, and provides a basic interface to the system.  In order to deploy the manager node, import the attached manager.tar file.  You can do this via Images > Import in Portainer or by running `docker import manager.tar manager`.

You should then deploy the manager node image to a container.  You can do this via the command `docker run -d --name manager -p 1337:1337 -v /var/run/docker.sock:/var/run/docker.sock manager`, or via Containers > Add Container and the following configuration:

* For Name, type manager
* Under Image configuration, click Advanced mode and type manager
* Under Network ports configuration, press "publish a new port" - enter 1337 in the host and container boxes
* Under Advanced container settings, select the Volumes tab and press map additional volumes.  Change the type to Bind and enter /var/run/docker.sock in the container and host blocks.
* Press Deploy the container (above Advanced container settings)

## Accessing the S3 Console
After a few moments, you should now see the s3 container show in the list of containers.  The manager node has automatically:
* Created a private network and added itself to it
* Pulled the minio/minio image
* Deployed the minio/minio image as s3

Note that a container is not normally capable of affecting the host system in this way - this is why we bound the docker.sock socket in the previous step.

You can now access the S3 console at http://127.0.0.1:9001.  Use the username and password minioadmin.  Note that in this test mode, S3 storage is not persisted and will be reset when the container is restarted.

S3 contains nine buckets:
* jars - a place to stage JAR files before being deployed
* output - the final output of each request
* pending1, pending2, pending3 - pending requests for each node
* result1, result2, result3 - results for each node
* pendingoutput - pending requests for the output

For more information on the purpose of these buckets, see the prototype documentation.

Upload all provided JAR files to the jars bucket.

## Deploying nodes
Once a JAR has been prepared in the jars bucket, it can be deployed using the following command:
`curl -v -X POST http://127.0.0.1:1337/node/<node> --data "<java version>:<jar filename>"`
* node being the name of the node that should be deployed (worker1, worker2, worker3, input, output)
* java version being the verison of Java to deploy (see https://hub.docker.com/_/openjdk?tab=tags for available images)
* jar filename being the filename that was uploaded to the jars bucket

In our case, we should deploy three worker nodes and the output node:
* curl -X POST http://127.0.0.1:1337/node/worker1 --data "17:WorkerNode_Good01.jar"
* curl -X POST http://127.0.0.1:1337/node/worker2 --data "17:WorkerNode_Good01.jar"
* curl -X POST http://127.0.0.1:1337/node/worker3 --data "17:WorkerNode_Good01.jar"
* curl -X POST http://127.0.0.1:1337/node/output --data "17:OutputNode.jar"

## Executing a request
In order to execute a request, upload a request to each of the following buckets:
* pending1
* pending2
* pending3
* pendingoutput

Example requests are given as 001.txt and 002.txt

After a few moments, the response will be given in the output bucket with the same filename.

To view the logs for each container, press the Logs button in the Containers page.

## Patching
In order to patch a worker node, run the same command used to deploy the node, for example:
* curl -X POST http://127.0.0.1:1337/node/worker1 --data "17:WorkerNode_Good02.jar"

Note that the worker node will be down while the node is being patched, but the system will remain stable and operational as long as a majority of worker nodes remain active.
