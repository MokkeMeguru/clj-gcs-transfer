# clj-gcs-transfer

The example code to communicate Google Cloud Storage

Notice: This repo is not library, but example to construct clean architecture-based application.

## Installation

```sh
lein deps
```

## Usage

You need to create service account to access google cloud storage, and also, get the secret json file which contains service account key.

```sh
export GOOGLE_APPLICATION_CREDENTIALS=<path to your secret json>

lein run -- --port 3030 --bucket-name <your google cloud storage bucket name>
```

### How to see help?

```sh
lein run -- --help
```

## More info

- Github Pages
  [https://mokkemeguru.github.io/clj-gcs-transfer/](https://mokkemeguru.github.io/clj-gcs-transfer/)
