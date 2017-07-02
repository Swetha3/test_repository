CI.Versions =
  v: (v) ->
    "#{v} #{CI.Versions[v]}"

  Firefox: "38.0.5"
  Chrome: "43.0.2357.130"
  chromedriver: "2.16"


  default_ruby: "1.9.3-p448"
  old_ruby: "1.8.7-p358"
  ruby_versions: [
    "1.8.7-p302",
    "1.8.7-p334",
    "1.8.7-p352",
    "1.8.7-p357",
    "1.8.7-p358",
    "1.8.7-p370",
    "1.8.7-p371",
    "1.8.7-p374",
    "ree-1.8.7-2011.02",
    "ree-1.8.7-2011.03",
    "ree-1.8.7-2011.12",
    "ree-1.8.7-2012.02",
    "1.9.2-p0",
    "1.9.2-p136",
    "1.9.2-p180",
    "1.9.2-p290",
    "1.9.2-p320",
    "1.9.2-p330",
    "1.9.3-p0-falcon",
    "1.9.3-p0",
    "1.9.3-p125",
    "1.9.3-p194",
    "1.9.3-p194-falcon",
    "1.9.3-p286",
    "1.9.3-p327",
    "1.9.3-p327-falcon",
    "1.9.3-p327-railsexpress",
    "1.9.3-p362",
    "1.9.3-p374",
    "1.9.3-p385",
    "1.9.3-p392",
    "1.9.3-p429",
    "1.9.3-p448",
    "1.9.3-p484",
    "1.9.3-p484-railsexpress",
    "1.9.3-p545",
    "1.9.3-p545-railsexpress",
    "1.9.3-p547",
    "1.9.3-p550",
    "1.9.3-p551",
    "2.0.0-p0",
    "2.0.0-p195",
    "2.0.0-p247",
    "2.0.0-p353",
    "2.0.0-p353-railsexpress",
    "2.0.0-p451",
    "2.0.0-p481",
    "2.0.0-p576",
    "2.0.0-p594",
    "2.0.0-p598",
    "2.0.0-p645",
    "2.1.0",
    "2.1.0-p0",
    "2.1.1",
    "2.1.2",
    "2.1.2-railsexpress",
    "2.1.3",
    "2.1.4",
    "2.1.5",
    "2.1.6",
    "2.1.7",
    "2.2.0-preview1",
    "2.2.0-preview2",
    "2.2.0",
    "2.2.1",
    "2.2.2",
    "2.2.3",
    "2.3.0",
    "jruby-1.7.19",
    "jruby-1.7.18",
    "jruby-1.7.16",
    "jruby-1.7.13",
    "jruby-1.7.12",
    "jruby-1.7.11",
    "jruby-1.7.0",
    "jruby-9.0.1.0",
    "jruby-9.0.3.0",
    "jruby-9.0.4.0",
    "rbx-2.2.6",
    "rbx-2.2.10",
    "rbx-2.5.2"
    ]
  rvm: "1.26.10"
  bundler: "1.9.5"
  cucumber: "1.2.0"
  rspec: "2.14.4"
  rake: "10.1.0"

  default_node: "0.10.33"
  node_versions: [
     "5.5.0",
     "5.4.0",
     "5.3.0",
     "5.2.0",
     "5.1.0",
     "5.0.0",
     "4.2.2",
     "4.1.0",
     "4.0.0",
     "iojs-v1.3.0",
     "iojs-v1.2.0",
     "0.12.0",
     "0.11.14",
     "0.11.13",
     "0.11.8",
     "0.10.34",
     "0.10.33",
     "0.10.32",
     "0.10.28",
     "0.10.26",
     "0.10.24",
     "0.10.22",
     "0.10.21",
     "0.10.20",
     "0.10.11",
     "0.10.5",
     "0.10.0",
     "0.8.24",
     "0.8.22",
     "0.8.19",
     "0.8.12",
     "0.8.2"
  ]

  lein: "2.5.1"
  ant: "1.8.2"
  maven: "3.2.1"

  default_python: "2.7.6"
  python: "2.7.6"
  python_versions: [
    "2.6.6",
    "2.6.8",
    "2.7",
    "2.7.3",
    "2.7.4",
    "2.7.5",
    "2.7.6",
    "2.7.7",
    "2.7.8",
    "2.7.9",
    "2.7.10",
    "3.1.5",
    "3.2",
    "3.2.5",
    "3.3.0",
    "3.3.2",
    "3.3.3",
    "3.4.0",
    "3.4.1",
    "3.4.2",
    "3.4.3",
    "3.5.0",
    "pypy-2.2.1",
    "pypy-2.3.1",
    "pypy-2.4.0",
    "pypy-2.5.0"
  ]
  pip: "7.0.3"
  virtualenv: "13.0.3"

  default_php: "5.3.10-1ubuntu3.7"
  php: "5.3.10-1ubuntu3.18"
  php_versions: [
    "7.0.4"
    "7.0.0RC7"
    "5.6.14"
    "5.6.5"
    "5.6.2"
    "5.5.21"
    "5.5.16"
    "5.5.15"
    "5.5.11"
    "5.5.9"
    "5.5.8"
    "5.5.7"
    "5.5.3"
    "5.5.2"
    "5.5.0"
    "5.4.37"
    "5.4.21"
    "5.4.19"
    "5.4.18"
    "5.4.15"
    "5.4.14"
    "5.4.13"
    "5.4.12"
    "5.4.11"
    "5.4.10"
    "5.4.9"
    "5.4.8"
    "5.4.7"
    "5.4.6"
    "5.4.5"
    "5.4.4"
    "5.3.25"
    "5.3.20"
    "5.3.10"
    "5.3.3"
  ]

  golang: '1.5.3'
  erlang: 'r14b04'

  default_java_package: "oraclejdk7"
  default_java_version: "1.7.0_55"
  java_packages: [
    { name: "Oracle JDK 8", package: "oraclejdk8", version: "1.8.0" },
    { name: "Oracle JDK 7", package: "oraclejdk7", version: "1.7.0_75", default: true },
    { name: "Oracle JDK 6", package: "oraclejdk6", version: "1.6.0_37" },
    { name: "Open JDK 7", package: "openjdk7" },
    { name: "Open JDK 6", package: "openjdk6" }
  ]

  gradle: "1.10"
  play: "2.2.1"
  scala_versions: [
    "0.11.3",
    "0.12.0",
    "0.12.1",
    "0.12.2",
    "0.12.3",
    "0.12.4",
    "0.13.0",
    "0.13.1"
    "0.13.2",
    "0.13.5",
    "0.13.6",
    "0.13.7",
    "0.13.8",
    "0.13.9"]

  solr: "4.3.1"
  postgresql: "9.4"
  mysql: "5.5.41"
  mongodb: "2.4.13"
  riak: "1.4.8-1"
  cassandra: "2.2.0"
  redis: "3.0.3"
  memcached: "1.4.13"
  sphinx: "2.0.4-release"
  elasticsearch: "0.90.2"
  beanstalkd: "1.4.6"
  couchbase: "2.0.0"
  couchdb: "1.3.0"
  neo4j: "2.2.2"
  rabbitmq: "3.4.4"
  zookeeper: "3.3.5"


  git: "1.8.5.6"

  ghc: "7.6.3"
  ghc_versions: [
    "7.4.2",
    "7.6.3",
    "7.8.2",
    "7.8.3",
    "7.8.4",
    "7.10.1",
    "7.10.2"]

  default_gcc: "4.6"
  default_g_plusx2: "4.6"
  gcc_g_plusx2_versions: [
    "4.6",
    "4.7",
    "4.8",
    "4.9"
  ]

  build_essential: "11.5"

  "g++": "4.6.3"
  casperjs: "1.0.9"
  phantomjs: "1.9.8"

  # you can copy-paste these from image-maker/src/circlevm/pallet/android.clj
  android_sdk_packages: [
    "platform-tools",
    "build-tools-23.0.2",
    "build-tools-23.0.1",
    "build-tools-22.0.1",
    "build-tools-21.1.2",
    "build-tools-20.0.0",
    "android-23",
    "android-22",
    "addon-google_apis-google-22",
    "sys-img-armeabi-v7a-android-22",
    "sys-img-armeabi-v7a-addon-google_apis-google-22",
    "android-21",
    "addon-google_apis-google-21",
    "sys-img-armeabi-v7a-android-21",
    "sys-img-armeabi-v7a-addon-google_apis-google-21",
    "android-20",
    "sys-img-armeabi-v7a-android-wear-20",
    "android-19",
    "sys-img-armeabi-v7a-android-19",
    "android-18",
    "sys-img-armeabi-v7a-android-18",
    "android-17",
    "sys-img-armeabi-v7a-android-17",
    "android-16",
    "sys-img-armeabi-v7a-android-16",
    "android-15",
    "sys-img-armeabi-v7a-android-15",
    "android-10",
    "extra-android-support",
    "extra-google-google_play_services",
    "extra-google-m2repository",
    "extra-android-m2repository"
  ]

  heroku: "3.42.44"
  awscli: "1.10.14"
  gcloud: "101.0.0"
  docker: "1.8.2"
  docker_compose: "1.5.1"