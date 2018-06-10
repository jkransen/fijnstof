scalaVersion := "2.12.4"

name := "fijnstof"
organization := "nl.kransen"
version := "1.0"
maintainer := "Jeroen Kransen <jeroen@kransen.nl>"
packageSummary := "SDS021 sensor reader for Domoticz"
packageDescription := """This software reads sensors and pushes readings to
  external services. Supported sensors: SDS021, supported services: Domoticz."""

libraryDependencies ++= Seq(
  "org.typelevel"          %% "cats-core"     % "1.0.1",
  "com.github.purejavacomm" % "purejavacomm"  % "1.0.1.RELEASE",
  "org.slf4j"               % "slf4j-log4j12" % "1.7.25",
  "com.typesafe"            % "config"        % "1.3.2",
  "org.scalaj"             %% "scalaj-http"   % "2.4.0")

// enablePlugins(JavaAppPackaging)

enablePlugins(JavaServerAppPackaging)
enablePlugins(SystemdPlugin)

//debianPackageDependencies in Debian ++= Seq("java8-runtime")

mappings in Universal += {
  // we are using the reference.conf as default application.conf
  // the user can override settings here
  // val conf = (resourceDirectory in Compile).value / "reference.conf"
  val conf = (resourceDirectory in Compile).value / "application.conf"
  conf -> "conf/application.conf"
}

daemonGroup in Linux := "dialout"

