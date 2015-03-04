The plugin is published to Sonatype, using [sbt-sonatype](https://github.com/xerial/sbt-sonatype).

# Tagging a new version of the project

    sbt release

# Publishing to Sonatype

    sbt publishSigned

# Releasing a new version (not needed for -SNAPSHOT versions).

After publishing as above:

    sbt sonatypeRelease
