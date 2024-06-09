import org.scalajs.linker.interface.ESVersion

val zioVersion = "2.1.2"

lazy val commonSettingsNoLibs = Seq(
  scalaVersion := "3.4.2",
)

lazy val commonSettings = commonSettingsNoLibs ++ Seq(
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),

  libraryDependencies ++= Seq(
    "org.typelevel" %%% "cats-core" % "2.12.0" % "optional",

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
    "-release", "9",
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

lazy val esexpr = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("esexpr"))
  .jvmConfigure(_.settings(jvmSettings))
  .jsConfigure(_.settings(jsSettings))
  .settings(
    commonSettings,
    compilerOptions,

    name := "esexpr",
  )

lazy val esexprJVM = esexpr.jvm
lazy val esexprJS = esexpr.js


