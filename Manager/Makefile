run: build
	docker run --name manager --rm -it -p 1337:1337 -v /var/run/docker.sock:/var/run/docker.sock manager
build: bin/run src/Application.php src/Docker.php src/RunCommand.php composer.lock
	docker buildx build . -t manager
