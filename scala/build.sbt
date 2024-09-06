import org.scalajs.linker.interface.ESVersion

val zioVersion = "2.1.7"

lazy val commonSettingsNoLibs = Seq(
  scalaVersion := "3.4.2",
)

ThisBuild / resolvers += Resolver.mavenLocal

lazy val commonSettings = commonSettingsNoLibs ++ Seq(
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),

  libraryDependencies ++= Seq(
    "dev.argon" %%% "argon-async-util" % "0.1.0-SNAPSHOT",

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
    libraryDependencies += "dev.argon" % "esexpr-java-runtime" % "0.1.1-SNAPSHOT",
  ))
  .jsConfigure(_.settings(jsSettings))
  .settings(
    commonSettings,
    compilerOptions,

    organization := "dev.argon.esexpr",
    version := "0.1.0-SNAPSHOT",

    name := "esexpr-scala-runtime",
  )

lazy val esexprJVM = esexpr.jvm
lazy val esexprJS = esexpr.js


