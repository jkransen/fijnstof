enablePlugins(JavaServerAppPackaging)
enablePlugins(SystemdPlugin)

name := "fijnstof"
version := "1.2"
organization := "nl.kransen"
maintainer := "Jeroen Kransen <jeroen@kransen.nl>"
packageSummary := "SDS021 sensor reader for Domoticz"
packageDescription := """This software reads sensors and pushes readings to external services.
  Supported sensors: SDS011, SDS021, MH-Z19(B).
  Supported services: Domoticz, Luftdaten."""
scalaVersion := "2.12.6"

val fs2Version = "1.0.4"
val sttpVersion = "2.0.0-RC3"
val circeVersion = "0.9.3"

libraryDependencies ++= Seq(
  "co.fs2"                       %% "fs2-core"                      % fs2Version,
  "co.fs2"                       %% "fs2-io"                        % fs2Version,
  "org.typelevel"                %% "cats-effect"                   % "2.0.0",
  "org.typelevel"                %% "cats-core"                     % "1.0.1",
  "com.softwaremill.sttp.client" %% "core"                          % sttpVersion,
  "com.softwaremill.sttp.client" %% "circe"                         % sttpVersion,
  "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % sttpVersion,
  "com.github.purejavacomm"       % "purejavacomm"                  % "1.0.1.RELEASE",
  "org.slf4j"                     % "slf4j-simple"                  % "1.7.25",
  "com.typesafe"                  % "config"                        % "1.3.2",
  "io.circe"                     %% "circe-core"                    % circeVersion,
  "io.circe"                     %% "circe-generic"                 % circeVersion,
  "io.circe"                     %% "circe-parser"                  % circeVersion,
  "com.iheart"                   %% "ficus"                         % "1.4.3",
  "org.scalatest"                %% "scalatest"                     % "3.0.5"           % Test,
  "org.scalamock"                %% "scalamock"                     % "4.1.0"           % Test
)

javaOptions in Universal ++= Seq(
  "-Dconfig.file=/usr/share/" + name.value + "/conf/application.conf"
)
// application.conf should not be packaged itself
mappings in Universal := {
  (mappings in Universal).value filter {
    case (file, name) =>  ! name.endsWith("application.conf")
  }
}

mappings in Universal += {
  // we are using the reference.conf as default application.conf
  // the user can override settings here
  val conf = (resourceDirectory in Compile).value / "reference.conf"
  conf -> "conf/application.conf"
}

daemonGroup in Linux := "dialout"

// add jvm parameter for typesafe config
bashScriptExtraDefines in Linux += """addJava "-Dconfig.file=${app_home}/../conf/application.conf""""

debianPackageDependencies in Debian ++= Seq("java8-runtime-headless")
//debianPackageDependencies in Debian ++= Seq("oracle-java8-jdk")
//openjdk-8-jre-headless

