import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {
  val appName = "address-lookup-frontend"

  lazy val appDependencies: Seq[ModuleID] = compile ++ test("test") ++ itDependencies

  val compile = Seq(
    ws,
    "uk.gov.hmrc"           %% "bootstrap-frontend-play-28"      % "7.12.0",
    "uk.gov.hmrc"           %% "play-frontend-hmrc"              % "5.2.0-play-28",
    "uk.gov.hmrc"           %% "http-caching-client"             % "10.0.0-play-28",
    "uk.gov.hmrc"           %% "play-conditional-form-mapping"   % "1.12.0-play-28",
    "com.github.tototoshi"  %% "scala-csv"                       % "1.3.10"
  ).map(_.withSources())

  def test(scope: String = "test") = Seq(
    "org.scalatest" %% "scalatest" % "3.0.8" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.jsoup" % "jsoup" % "1.8.1" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % scope,
    "org.mockito" % "mockito-all" % "2.0.2-beta" % scope
  ).map(_.withSources())

  def itDependencies = test("it") ++
      Seq("com.github.tomakehurst" % "wiremock-jre8" % "2.26.1" % "it")

}
