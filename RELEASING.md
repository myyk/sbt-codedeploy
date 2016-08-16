The plugin is published to Bintray.

## Publishing

Run this to setup your Bintray credentials:

    sbt bintrayChangeCredentials

## Tagging a New Version

    sbt release

## Publishing to Sonatype

    sbt publishSigned

## Releasing a new version (not needed for -SNAPSHOT versions)

After publishing as above:

    sbt sonatypeRelease
