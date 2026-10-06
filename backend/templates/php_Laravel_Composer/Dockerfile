FROM php:8.3-cli
WORKDIR /app
COPY --from=composer:2 /usr/bin/composer /usr/bin/composer
COPY . .
RUN composer install --no-interaction
CMD ["php","artisan","serve","--host=0.0.0.0","--port=8080"]
