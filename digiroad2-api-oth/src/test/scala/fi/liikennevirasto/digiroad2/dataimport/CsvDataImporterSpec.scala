package fi.liikennevirasto.digiroad2.dataimport

import java.io.{ByteArrayInputStream, InputStream}

import javax.sql.DataSource
import fi.liikennevirasto.digiroad2.asset.{Motorway, Municipality, State, TrafficDirection}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, Tag}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import fi.liikennevirasto.digiroad2.Digiroad2Context.userProvider
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService

object sTestTransactions {
  def runWithRollback(ds: DataSource = OracleDatabase.ds)(f: => Unit): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }
  def withDynTransaction[T](ds: DataSource = OracleDatabase.ds)(f: => T): T = {
    Database.forDataSource(ds).withDynTransaction {
      f
    }
  }
  def withDynSession[T](ds: DataSource = OracleDatabase.ds)(f: => T): T = {
    Database.forDataSource(ds).withDynSession {
      f
    }
  }
}

class CsvDataImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val testUserProvider = userProvider
  val roadLinkCsvImporter = importerWithNullService()

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val vvHRoadlink = Seq(VVHRoadlink(1611400, 235, Seq(Point(2, 2), Point(4, 4)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers))
  val roadLink = Seq(RoadLink(1, Seq(Point(2, 2), Point(4, 4)), 3.5, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None))

  when(mockRoadLinkService.getClosestRoadlinkForCarTrafficFromVVH(any[User], any[Point])).thenReturn(vvHRoadlink)
  when(mockRoadLinkService.enrichRoadLinksFromVVH(any[Seq[VVHRoadlink]], any[Seq[ChangeInfo]])).thenReturn(roadLink)
  val trafficSignCsvImporter : TrafficSignCsvImporter = new TrafficSignCsvImporter {
    override val roadLinkService = mockRoadLinkService
      }

  private def importerWithNullService() : RoadLinkCsvImporter = {
    new RoadLinkCsvImporter {
      override lazy val vvhClient: VVHClient = MockitoSugar.mock[VVHClient]
      override def withDynTransaction[T](f: => T): T = f
    }
  }

  before {
    testUserProvider.setCurrentUser(User(id = 1, username = "CsvDataImportApiSpec", configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen))))
  }

  test("rowToString works correctly for few basic fields") {
    roadLinkCsvImporter.rowToString(Map(
      "Hallinnollinen luokka" -> "Hallinnollinen",
      "Toiminnallinen luokka" -> "Toiminnallinen"
    )) should equal("Hallinnollinen luokka: 'Hallinnollinen', Toiminnallinen luokka: 'Toiminnallinen'")
  }

  val defaultKeys = "Linkin ID" :: roadLinkCsvImporter.mappings.keys.toList

  val defaultValues = defaultKeys.map { key => key -> "" }.toMap

  private def createCSV(assets: Map[String, Any]*): String = {
    val headers = defaultKeys.mkString(";") + "\n"
    val rows = assets.map { asset =>
      defaultKeys.map { key => asset.getOrElse(key, "") }.mkString(";")
    }.mkString("\n")
    headers + rows
  }

  private def createCsvForTrafficSigns(assets: Map[String, Any]*): String = {
    val headers = trafficSignCsvImporter.mappings.keys.toList.mkString(";") + "\n"
    val rows = assets.map { asset =>
      trafficSignCsvImporter.mappings.keys.toList.map { key => asset.getOrElse(key, "") }.mkString(";")
    }.mkString("\n")
    headers + rows
  }

  def runWithRollback(test: => Unit): Unit = sTestTransactions.runWithRollback(roadLinkCsvImporter.dataSource)(test)

  test("validation fails if field type \"Linkin ID\" is not filled", Tag("db")) {
    val roadLinkFields = Map("Tien nimi (suomi)" -> "nimi", "Liikennevirran suunta" -> "5")
    val invalidCsv = csvToInputStream(createCSV(roadLinkFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(roadLinkCsvImporter.ImportResult(
      malformedLinks = List(roadLinkCsvImporter.MalformedLink(
        malformedParameters = List("Linkin ID"),
        csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ roadLinkFields)))))
  }

  test("validation fails if type contains illegal characters", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
    when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))

    val assetFields = Map("Linkin ID" -> 1, "Liikennevirran suunta" -> "a")
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(roadLinkCsvImporter.ImportResult(
      malformedLinks = List(roadLinkCsvImporter.MalformedLink(
        malformedParameters = List("Liikennevirran suunta"),
        csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("validation fails if administrative class = 1 on VVH", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = State
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
    when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))

    val assetFields = Map("Linkin ID" -> 1, "Hallinnollinen luokka" -> 2)
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(roadLinkCsvImporter.ImportResult(
      excludedLinks = List(roadLinkCsvImporter.ExcludedLink(unauthorizedAdminClass = List("AdminClass value State found on  VVH"), csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("validation fails if administrative class = 1 on CSV", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
    when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))

    val assetFields = Map("Linkin ID" -> 1, "Hallinnollinen luokka" -> 1)
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(roadLinkCsvImporter.ImportResult(
      excludedLinks = List(roadLinkCsvImporter.ExcludedLink(unauthorizedAdminClass = List("AdminClass value State found on  CSV"), csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("validation fails if administrative class = 1 on CSV and VVH", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = State
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
    when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))

    val assetFields = Map("Linkin ID" -> 1, "Hallinnollinen luokka" -> 1)
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(roadLinkCsvImporter.ImportResult(
      excludedLinks = List(roadLinkCsvImporter.ExcludedLink(unauthorizedAdminClass = List("AdminClass value State found on  VVH", "AdminClass value State found on  CSV"), csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("update functionalClass by CSV import", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    runWithRollback {
      when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))

      val link_id = 1000
      val functionalClassValue = 3
      RoadLinkDAO.insert(RoadLinkDAO.FunctionalClass, link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Toiminnallinen luokka" -> functionalClassValue)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(roadLinkCsvImporter.ImportResult())
      RoadLinkDAO.get(RoadLinkDAO.FunctionalClass, link_id) should equal (Some(functionalClassValue))
    }
  }

  test("insert functionalClass by CSV import", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    runWithRollback {
      when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))

      val link_id = 1000
      val functionalClassValue = 3

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Toiminnallinen luokka" -> functionalClassValue)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(roadLinkCsvImporter.ImportResult())
      RoadLinkDAO.get(RoadLinkDAO.FunctionalClass, link_id) should equal (Some(functionalClassValue))
    }
  }

  test("update linkType by CSV import", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    runWithRollback {
      when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))
      val link_id = 1000
      val linkTypeValue = 3
      RoadLinkDAO.insert(RoadLinkDAO.LinkType, link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Tielinkin tyyppi" ->linkTypeValue)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(roadLinkCsvImporter.ImportResult())
      RoadLinkDAO.get(RoadLinkDAO.LinkType, link_id) should equal (Some(linkTypeValue))
    }
  }

  test("insert linkType by CSV import", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    runWithRollback {
      when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))
      val link_id = 1000
      val linkTypeValue = 3

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Tielinkin tyyppi" -> linkTypeValue)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(roadLinkCsvImporter.ImportResult())
      RoadLinkDAO.get(RoadLinkDAO.LinkType, link_id) should equal (Some(linkTypeValue))
    }
  }

  test("delete trafficDirection (when already exist in db) by CSV import", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)

    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    runWithRollback {
      when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.updateVVHFeatures(any[Map[String , String]])).thenReturn( Left(List(Map("key" -> "value"))))
      when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))
      val link_id = 1611388
      RoadLinkDAO.insert(RoadLinkDAO.TrafficDirection, link_id, Some("unit_test"), 1)
      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Liikennevirran suunta" -> 3)))

      roadLinkCsvImporter.importLinkAttribute(csv) should equal(roadLinkCsvImporter.ImportResult())
      RoadLinkDAO.get(RoadLinkDAO.TrafficDirection, link_id) should equal (None)
    }
  }

  test("update OTH and VVH by CSV import", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)

    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    runWithRollback {
      when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.updateVVHFeatures(any[Map[String , String]])).thenReturn( Left(List(Map("key" -> "value"))))
      when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))
      val link_id = 1000
      val linkTypeValue = 3
      RoadLinkDAO.insert(RoadLinkDAO.LinkType, link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Tielinkin tyyppi" -> linkTypeValue, "Kuntanumero" -> 2,
        "Liikennevirran suunta" -> 1, "Hallinnollinen luokka" -> 2)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(roadLinkCsvImporter.ImportResult())
      RoadLinkDAO.get(RoadLinkDAO.LinkType, link_id) should equal(Some(linkTypeValue))
    }
  }

  test("validation for traffic sign import fails if type contains illegal characters", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "a")
    val invalidCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))
    val defaultValues = trafficSignCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap

    trafficSignCsvImporter.importTrafficSigns(invalidCsv, Set()) should equal(trafficSignCsvImporter.ImportResult(
      malformedAssets = List(trafficSignCsvImporter.MalformedAsset(
        malformedParameters = List("liikennemerkin tyyppi"),
        csvRow = trafficSignCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("validation for traffic sign import fails if mandatory parameters are missing", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> "", "koordinaatti y" -> "", "liikennemerkin tyyppi" -> "")
    val invalidCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))
    val defaultValues = trafficSignCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap
    val csvRow = trafficSignCsvImporter.rowToString(defaultValues ++ assetFields)
    val assets = trafficSignCsvImporter.importTrafficSigns(invalidCsv, Set())

    assets.malformedAssets.flatMap(_.malformedParameters) should contain allOf ("koordinaatti x", "koordinaatti y", "liikennemerkin tyyppi")
    assets.malformedAssets.foreach {
      asset =>
        asset.csvRow should be (trafficSignCsvImporter.rowToString(defaultValues ++ assetFields))
    }
  }

  test("validation for traffic sign import fails if user try to create in an authorize municipality", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "671")
    val invalidCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))
    val defaultValues = trafficSignCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap
    when(mockRoadLinkService.getClosestRoadlinkForCarTrafficFromVVH(any[User], any[Point])).thenReturn(Seq())

    trafficSignCsvImporter.importTrafficSigns(invalidCsv, Set()) should equal(trafficSignCsvImporter.ImportResult(
      notImportedData = List(trafficSignCsvImporter.NotImportedData(
        reason = "Unauthorized Municipality Or RoadLind inexistent near of Asset",
        csvRow = trafficSignCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("filter road links considering bearing in traffic sign and bearing of the road links, same bearing, validity direction and 10 meter radius of the sign") {
    val newLinkId1 = 5000
    val geometryPoints1 = List(Point(60.0, 35.0), Point(60.0, 15.0), Point(50.0, 10.0), Point(30.0, 15.0), Point(10.0, 25.0))
    val trafficDirection1 = TrafficDirection.AgainstDigitizing
    val newLinkId2 = 5001
    val geometryPoints2 = List(Point(40.0, 40.0), Point(90.0, 40.0))
    val trafficDirection2 = TrafficDirection.BothDirections
    val newLinkId3 = 5002
    val geometryPoints3 = List(Point(80.0, 10.0), Point(80.0, 30.0))
    val trafficDirection3 = TrafficDirection.TowardsDigitizing

    val trafficSignBearing = Some(190)
    val trafficSignCoordinates = Point(70.0, 32.0)
    val municipalityCode = 564
    val administrativeClass = Municipality
    val attributes = Map("OBJECTID" -> BigInt(99))

    val newVVHRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, geometryPoints1, administrativeClass, trafficDirection1, FeatureClass.DrivePath, None, attributes)
    val newVVHRoadLink2 = VVHRoadlink(newLinkId2, municipalityCode, geometryPoints2, administrativeClass, trafficDirection2, FeatureClass.DrivePath, None, attributes)
    val newVVHRoadLink3 = VVHRoadlink(newLinkId3, municipalityCode, geometryPoints3, administrativeClass, trafficDirection3, FeatureClass.DrivePath, None, attributes)
    val vVHRoadLinkSeq = Seq(newVVHRoadLink1, newVVHRoadLink2, newVVHRoadLink3)

    val newRoadLink1 = RoadLink(newLinkId1, geometryPoints1, 0.0, administrativeClass, 1, trafficDirection1, Motorway, None, None)
    val newRoadLink2 = RoadLink(newLinkId2, geometryPoints2, 0.0, administrativeClass, 1, trafficDirection2, Motorway, None, None)
    val newRoadLink3 = RoadLink(newLinkId3, geometryPoints3, 0.0, administrativeClass, 1, trafficDirection3, Motorway, None, None)
    val roadLinkSeq = Seq(newRoadLink1, newRoadLink2, newRoadLink3)

    when(mockRoadLinkService.enrichRoadLinksFromVVH(any[Seq[VVHRoadlink]], any[Seq[ChangeInfo]])).thenReturn(roadLinkSeq)
    when(mockRoadLinkService.getClosestRoadlinkForCarTrafficFromVVH(any[User], any[Point])).thenReturn(vVHRoadLinkSeq)

    val (roadLinksFilteredByBearing, enrichedRoadLinksFilteredByBearing) =
      trafficSignCsvImporter.getRightRoadLinkUsingBearing(trafficSignBearing, trafficSignCoordinates)

    roadLinksFilteredByBearing.size should be (1)
    roadLinksFilteredByBearing.head.linkId should be (newLinkId1)

    enrichedRoadLinksFilteredByBearing.size should be (1)
    enrichedRoadLinksFilteredByBearing.head.linkId should be (newLinkId1)

    roadLinksFilteredByBearing.exists { rl => enrichedRoadLinksFilteredByBearing.exists(_.linkId == rl.linkId) } should be(true)
  }

  test("filter road links considering bearing in traffic sign and bearing of the road links, different bearing in all") {
    val newLinkId1 = 5000
    val geometryPoints1 = List(Point(60.0, 35.0), Point(60.0, 15.0), Point(50.0, 10.0), Point(30.0, 15.0), Point(10.0, 25.0))
    val trafficDirection1 = TrafficDirection.TowardsDigitizing
    val newLinkId2 = 5001
    val geometryPoints2 = List(Point(40.0, 40.0), Point(90.0, 40.0))
    val trafficDirection2 = TrafficDirection.TowardsDigitizing
    val newLinkId3 = 5002
    val geometryPoints3 = List(Point(80.0, 10.0), Point(80.0, 30.0))
    val trafficDirection3 = TrafficDirection.TowardsDigitizing

    val trafficSignBearing = Some(20)
    val trafficSignCoordinates = Point(70.0, 32.0)
    val municipalityCode = 564
    val administrativeClass = Municipality
    val attributes = Map("OBJECTID" -> BigInt(99))

    val newVVHRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, geometryPoints1, administrativeClass, trafficDirection1, FeatureClass.DrivePath, None, attributes)
    val newVVHRoadLink2 = VVHRoadlink(newLinkId2, municipalityCode, geometryPoints2, administrativeClass, trafficDirection2, FeatureClass.DrivePath, None, attributes)
    val newVVHRoadLink3 = VVHRoadlink(newLinkId3, municipalityCode, geometryPoints3, administrativeClass, trafficDirection3, FeatureClass.DrivePath, None, attributes)
    val vVHRoadLinkSeq = Seq(newVVHRoadLink1, newVVHRoadLink2, newVVHRoadLink3)

    val newRoadLink1 = RoadLink(newLinkId1, geometryPoints1, 0.0, administrativeClass, 1, trafficDirection1, Motorway, None, None)
    val newRoadLink2 = RoadLink(newLinkId2, geometryPoints2, 0.0, administrativeClass, 1, trafficDirection2, Motorway, None, None)
    val newRoadLink3 = RoadLink(newLinkId3, geometryPoints3, 0.0, administrativeClass, 1, trafficDirection3, Motorway, None, None)
    val roadLinkSeq = Seq(newRoadLink1, newRoadLink2, newRoadLink3)

    when(mockRoadLinkService.enrichRoadLinksFromVVH(any[Seq[VVHRoadlink]], any[Seq[ChangeInfo]])).thenReturn(roadLinkSeq)
    when(mockRoadLinkService.getClosestRoadlinkForCarTrafficFromVVH(any[User], any[Point])).thenReturn(vVHRoadLinkSeq)

    val (roadLinksFilteredByBearing, enrichedRoadLinksFilteredByBearing) =
      trafficSignCsvImporter.getRightRoadLinkUsingBearing(trafficSignBearing, trafficSignCoordinates)

    roadLinksFilteredByBearing should be (vVHRoadLinkSeq)
    enrichedRoadLinksFilteredByBearing should be (roadLinkSeq)
  }

  test("filter road links considering bearing in traffic sign and bearing of the road links, road link with both traffic direction") {
    val newLinkId1 = 5000
    val geometryPoints1 = List(Point(60.0, 35.0), Point(60.0, 15.0), Point(50.0, 10.0), Point(30.0, 15.0), Point(10.0, 25.0))
    val trafficDirection1 = TrafficDirection.BothDirections
    val newLinkId2 = 5001
    val geometryPoints2 = List(Point(40.0, 40.0), Point(90.0, 40.0))
    val trafficDirection2 = TrafficDirection.TowardsDigitizing
    val newLinkId3 = 5002
    val geometryPoints3 = List(Point(80.0, 10.0), Point(80.0, 30.0))
    val trafficDirection3 = TrafficDirection.TowardsDigitizing

    val trafficSignBearing = Some(20)
    val trafficSignCoordinates = Point(70.0, 32.0)
    val municipalityCode = 564
    val administrativeClass = Municipality
    val attributes = Map("OBJECTID" -> BigInt(99))


    val newVVHRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, geometryPoints1, administrativeClass, trafficDirection1, FeatureClass.DrivePath, None, attributes)
    val newVVHRoadLink2 = VVHRoadlink(newLinkId2, municipalityCode, geometryPoints2, administrativeClass, trafficDirection2, FeatureClass.DrivePath, None, attributes)
    val newVVHRoadLink3 = VVHRoadlink(newLinkId3, municipalityCode, geometryPoints3, administrativeClass, trafficDirection3, FeatureClass.DrivePath, None, attributes)
    val vVHRoadLinkSeq = Seq(newVVHRoadLink1, newVVHRoadLink2, newVVHRoadLink3)

    val newRoadLink1 = RoadLink(newLinkId1, geometryPoints1, 0.0, administrativeClass, 1, trafficDirection1, Motorway, None, None)
    val newRoadLink2 = RoadLink(newLinkId2, geometryPoints2, 0.0, administrativeClass, 1, trafficDirection2, Motorway, None, None)
    val newRoadLink3 = RoadLink(newLinkId3, geometryPoints3, 0.0, administrativeClass, 1, trafficDirection3, Motorway, None, None)
    val roadLinkSeq = Seq(newRoadLink1, newRoadLink2, newRoadLink3)

    when(mockRoadLinkService.getClosestRoadlinkForCarTrafficFromVVH(any[User], any[Point])).thenReturn(vVHRoadLinkSeq)
    when(mockRoadLinkService.enrichRoadLinksFromVVH(any[Seq[VVHRoadlink]], any[Seq[ChangeInfo]])).thenReturn(roadLinkSeq)

    val (roadLinksFilteredByBearing, enrichedRoadLinksFilteredByBearing) =
      trafficSignCsvImporter.getRightRoadLinkUsingBearing(trafficSignBearing, trafficSignCoordinates)

    roadLinksFilteredByBearing.size should be (1)
    roadLinksFilteredByBearing.head.linkId should be (newLinkId1)

    enrichedRoadLinksFilteredByBearing.size should be (1)
    enrichedRoadLinksFilteredByBearing.head.linkId should be (newLinkId1)

    roadLinksFilteredByBearing.exists { rl => enrichedRoadLinksFilteredByBearing.exists(_.linkId == rl.linkId) } should be(true)
  }

  private def csvToInputStream(csv: String): InputStream = new ByteArrayInputStream(csv.getBytes())
}
