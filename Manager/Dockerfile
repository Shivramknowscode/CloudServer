FROM php:8.1.0RC4-alpine
RUN docker-php-ext-install pcntl
# Install composer
RUN curl https://getcomposer.org/installer -o installer
RUN php installer
COPY composer.json composer.json
COPY composer.lock composer.lock
RUN php composer.phar install

# Amazon is stupid and assumes we can throw random subdomains in places and everything will just work
RUN sed -i 's/isBucketDnsCompatible/isBucketDnsCompatible(){return false;} public static function _isBucketDnsCompatible/' ./vendor/aws/aws-sdk-php/src/S3/S3Client.php
COPY src src
COPY bin bin
CMD php bin/run