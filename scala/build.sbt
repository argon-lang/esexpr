import org.scalajs.linker.interface.ESVersion

import _root_.io.circe.Json

import scala.sys.process.Process

val zioVersion = "2.1.19"

name := "esexpr"


ThisBuild / scalaVersion := "3.7.1"

publish / skip := true

ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / credentials += Credentials(
  "GnuPG Key ID",
  "gpg",
  "3460F237EA4AEB29F91F0638133C9C282D54701F",
  "ignored",
)
ThisBuild / resolvers += Resolver.mavenLocal

lazy val commonSettings = Seq(
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),

  libraryDependencies ++= Seq(
    "dev.argon" %%% "argon-async-util" % "2.0.0",

    "dev.zio" %%% "zio" % zioVersion,
    "dev.zio" %%% "zio-streams" % zioVersion,
    "org.typelevel" %%% "cats-core" % "2.13.0",

    "dev.zio" %%% "zio-test" % zioVersion % "test",
    "dev.zio" %%% "zio-test-sbt" % zioVersion % "test",
  ),

)

lazy val jvmSettings = Seq(
  fork := true,
)

lazy val jsSettings = Seq(
  scalaJSLinkerConfig ~= {
    _.withESFeatures(_.withESVersion(ESVersion.ES2018))
      .withModuleKind(ModuleKind.ESModule)
  },
)

lazy val compilerOptions = Seq(


  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-release", "22",
    "-source", "future",
    "-language:higherKinds",
    "-language:existentials",
    "-language:implicitConversions",
    "-language:strictEquality",
    "-deprecation",
    "-feature",
    "-Ycheck-all-patmat",
    "-Yretain-trees",
    "-Yexplicit-nulls",
    "-Xmax-inlines", "128",
    "-Wconf:id=E029:e,id=E165:e,id=E190:e,cat=unchecked:e,cat=deprecation:e",
  ),

  Compile / packageBin / packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "dev.argon.nobleidl.core.scala"),
)

lazy val npmSetup = taskKey[Unit]("Setup npm dependencies before running tests")



lazy val esexpr = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full).in(file("esexpr"))
  .jvmConfigure(_.settings(
    jvmSettings,
    libraryDependencies ++= Seq(
      "dev.argon.esexpr" % "esexpr-java-runtime" % "0.3.1-SNAPSHOT",
      "commons-io" % "commons-io" % "2.20.0" % Test,
    ),
  ))
  .jsConfigure(_.settings(
    jsSettings,

    Test / npmSetup := {
      val targetDir = (Test / target).value
      val packageJsonFile = targetDir / "package.json"
      val npmPackagePath = baseDirectory.value / "../../../js/"
  
      val packageJson = Json.obj(
        "name" -> Json.fromString("@argon-lang/esexpr-scala"),
        "version" -> Json.fromString("1.0.0"),
        "type" -> Json.fromString("module"),
        "dependencies" -> Json.obj(
          "@argon-lang/esexpr" -> Json.fromString(s"file:${npmPackagePath.getAbsolutePath}")
        )
      )

      IO.write(packageJsonFile, packageJson.spaces2)
      
      val npmInstallCmd = Seq("npm", "install")
      Process(npmInstallCmd, targetDir).!
    },

    Test / test := (Test / test).dependsOn(Test / npmSetup).value,
  ))
  .settings(
    commonSettings,
    compilerOptions,

    name := "ESExpr Scala Runtime",
    organization := "dev.argon.esexpr",
    version := "0.3.1-SNAPSHOT",
    Compile / packageBin / packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "dev.argon.esexpr.scala"),

    description := "ESExpr Scala runtime library",
    homepage := Some(url("https://github.com/argon-lang/esexpr")),

    licenses := Seq(
      "Apache License, Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")
    ),


    publishTo := Some(MavenCache("target-repo", (Compile / target).value / "repo")),

    scmInfo := Some(ScmInfo(
      connection = "scm:git:git@github.com:argon-lang/esexpr.git",
      devConnection = "scm:git:git@github.com:argon-lang/esexpr.git",
      browseUrl = url("https://github.com/argon-lang/esexpr/tree/master/scala"),
    )),

    pomExtra := (
      <developers>
        <developer>
          <name>argon-dev</name>
          <email>argon@argon.dev</email>
          <organization>argon-lang</organization>
          <organizationUrl>https://argon.dev</organizationUrl>
        </developer>
      </developers>
    ),


    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % "0.14.14" % Test,
      "io.circe" %%% "circe-generic" % "0.14.14" % Test,
      "io.circe" %%% "circe-parser" % "0.14.14" % Test,
    )


  )

lazy val esexprJVM = esexpr.jvm
lazy val esexprJS = esexpr.js


