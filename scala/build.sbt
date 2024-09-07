import org.scalajs.linker.interface.ESVersion

val zioVersion = "2.1.9"

lazy val commonSettingsNoLibs = Seq(
  scalaVersion := "3.5.0",
)

publish / skip := true

ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / credentials += Credentials(
  "GnuPG Key ID",
  "gpg",
  "3460F237EA4AEB29F91F0638133C9C282D54701F",
  "ignored",
)


lazy val commonSettings = commonSettingsNoLibs ++ Seq(
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),

  libraryDependencies ++= Seq(
    "dev.argon" %%% "argon-async-util" % "0.1.0",

    "dev.zio" %%% "zio" % zioVersion,
    "dev.zio" %%% "zio-streams" % zioVersion,
    "org.typelevel" %%% "cats-core" % "2.12.0",

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
  }
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

)

lazy val esexpr = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full).in(file("esexpr"))
  .jvmConfigure(_.settings(
    jvmSettings,
    libraryDependencies += "dev.argon.esexpr" % "esexpr-java-runtime" % "0.1.0",
  ))
  .jsConfigure(_.settings(jsSettings))
  .settings(
    commonSettings,
    compilerOptions,

    name := "ESExpr Scala Runtime",
    organization := "dev.argon.esexpr",
    version := "0.1.0",

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

  )

lazy val esexprJVM = esexpr.jvm
lazy val esexprJS = esexpr.js


