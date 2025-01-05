# Notification for new repository

Project is renamed to https://github.com/asukiaaa/clj-server-device-log

# clj-server-practice

A project to practice of creating web server in clojure.

# TODO

Check devices are deleted if device group is deleted
Check raw_device_logs are deleted if device is deleted

## Setup

Install docker-compose

## Usage

Run

```bash
docker-compose up
```

See http://localhost:3000


Access to mariadb

```bash
./bin/mariadb
```

## Test

```bash
docker-compose run back clj -X:test:runner
```

## Deploy to heroku

### Setup

```
heroku login
heroku plugins:install java
```

### Deploy

```bash
./bin/deploy-heroku your-heroku-app-name
```

## References

- [clojure cliプロジェクトをherokuで動かす](https://asukiaaa.blogspot.com/2022/03/clojure-cli-on-heroku.html)
