package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri.TRTrafficSignType
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.OracleUserProvider
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{ManoeuvreService, ProhibitionService}
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, GeometryUtils, Point}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

class TrafficSignServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
  def toRoadLink(l: VVHRoadlink) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }

  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val batchProcessName = "batch_process_trafficSigns"
  private val typePublicId = "trafficSigns_type"
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockUserProvider = MockitoSugar.mock[OracleUserProvider]
  val vvHRoadlink2 = Seq(VVHRoadlink(1611400, 235, Seq(Point(2, 2), Point(4, 4)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers))
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq(
    VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))
  when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(any[Long], any[Boolean])).thenReturn(Seq(
    VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)
  when(mockRoadLinkService.getClosestRoadlinkForCarTrafficFromVVH(any[User], any[Point])).thenReturn(Seq(
    VVHRoadlink(1611400, 235, Seq(Point(2, 2), Point(4, 4)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)))
  when(mockRoadLinkService.getRoadLinkFromVVH(1611400)).thenReturn(Seq(
    VVHRoadlink(1611400, 235, Seq(Point(2, 2), Point(4, 4)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)
  when(mockUserProvider.getCurrentUser()).thenReturn(testUser)

  when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(1191950690)).thenReturn(Seq(
    VVHRoadlink(1191950690, 235, Seq(Point(373500.349, 6677657.152), Point(373494.182, 6677669.918)), Private,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)
  val userProvider = new OracleUserProvider
  val mockManoeuvreService = MockitoSugar.mock[ManoeuvreService]
  val mockProhibitionService = MockitoSugar.mock[ProhibitionService]
  val service = new TrafficSignService(mockRoadLinkService, mockUserProvider, new DummyEventBus, mockManoeuvreService, mockProhibitionService) {
    override def withDynTransaction[T](f: => T): T = f

    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

  test("Can fetch by bounding box") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(), Nil))

    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374466.5, 6677346.5), Point(374467.5, 6677347.5))).head
      result.id should equal(600073)
      result.linkId should equal(1611317)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }
  }

  test("Can fetch by municipality") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(235)).thenReturn((Seq(
      VVHRoadlink(388553074, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink), Nil))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600073).get

      result.id should equal(600073)
      result.linkId should equal(1611317)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }
  }

  test("Expire Traffic Sign") {
    runWithRollback {
      service.getPersistedAssetsByIds(Set(600073)).length should be(1)
      service.expire(600073, testUser.username)
      service.getPersistedAssetsByIds(Set(600073)) should be(Nil)
    }
  }

  test("Create new Traffic Sign") {
    runWithRollback {
      val properties = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("1"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue("80"))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Additional Info for test"))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 1, None), testUser.username, roadLink)

      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head

      asset.id should be(id)
      asset.linkId should be(388553075)
      asset.lon should be(2)
      asset.lat should be(0)
      asset.mValue should be(2)
      asset.floating should be(false)
      asset.municipalityCode should be(235)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("80")
      asset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.propertyValue should be ("Additional Info for test")
      asset.createdBy should be(Some(testUser.username))
      asset.createdAt shouldBe defined
    }
  }

  test("Update Traffic Sign") {
    runWithRollback {
      val trafficSign = service.getById(600073).get
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val updatedProperties = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("2"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue("90"))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Updated Additional Info for test"))))
      val updated = IncomingTrafficSign(trafficSign.lon, trafficSign.lat, trafficSign.linkId, updatedProperties, 1, None)

      service.update(trafficSign.id, updated, roadLink, "unit_test")
      val updatedTrafficSign = service.getById(600073).get

      updatedTrafficSign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("2")
      updatedTrafficSign.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("90")
      updatedTrafficSign.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.propertyValue should be ("Updated Additional Info for test")
      updatedTrafficSign.id should equal(updatedTrafficSign.id)
      updatedTrafficSign.modifiedBy should equal(Some("unit_test"))
      updatedTrafficSign.modifiedAt shouldBe defined
    }
  }

  test("Update traffic sign with geometry changes"){
    runWithRollback {

      val properties = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("1"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue("80"))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Additional Info for test"))))

      val propertiesToUpdate = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("2"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue("60"))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Additional Info for test"))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(0.0, 20.0, 388553075, properties, 1, None), "jakke", roadLink )
      val oldAsset = service.getPersistedAssetsByIds(Set(id)).head
      oldAsset.modifiedAt.isDefined should equal(false)

      val newId = service.update(id, IncomingTrafficSign(0.0, 10.0, 388553075, propertiesToUpdate, 1, None), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should not be id
      updatedAsset.lon should equal (0.0)
      updatedAsset.lat should equal (10.0)
      updatedAsset.createdBy should equal (oldAsset.createdBy)
      updatedAsset.createdAt should equal (oldAsset.createdAt)
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.modifiedAt.isDefined should equal(true)
      updatedAsset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("2")
      updatedAsset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("60")
      updatedAsset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.propertyValue should be ("Additional Info for test")
    }
  }

  test("Update traffic sign without geometry changes"){
    runWithRollback {
      val properties = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("1"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue("80"))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Additional Info for test"))))

      val propertiesToUpdate = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("2"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue("60"))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Additional Info for update test"))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(0.0, 20.0, 388553075, properties, 1, None), "jakke", roadLink )
      val asset = service.getPersistedAssetsByIds(Set(id)).head

      val newId = service.update(id, IncomingTrafficSign(0.0, 20.0, 388553075, propertiesToUpdate, 1, None), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should be (id)
      updatedAsset.lon should be (asset.lon)
      updatedAsset.lat should be (asset.lat)
      updatedAsset.createdBy should equal (Some("jakke"))
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("2")
      updatedAsset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("60")
      updatedAsset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.propertyValue should be ("Additional Info for update test")
    }
  }

  test("Create traffic sign with direction towards digitizing using coordinates without asset bearing") {
    /*mock road link is set to (2,2), (4,4), so this asset is set to go towards digitizing*/
    runWithRollback {
      val id = service.createFromCoordinates(3, 2, TRTrafficSignType.SpeedLimit, Some(100), Some(false), TrafficDirection.UnknownDirection, None, None, vvHRoadlink2)
      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("100")
      asset.validityDirection should be (TowardsDigitizing.value)
      asset.bearing.get should be (45)
    }
  }

  test("Create traffic sign with direction against digitizing using coordinates without asset bearing") {
     /*mock road link is set to (2,2), (4,4), so this asset is set to go against digitizing*/
    runWithRollback {
      val id = service.createFromCoordinates(3, 4, TRTrafficSignType.SpeedLimit, Some(100), Some(false), TrafficDirection.UnknownDirection, None, None, vvHRoadlink2)
      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("100")
      asset.validityDirection should be (AgainstDigitizing.value)
    }
  }

  test("Create traffic sign with direction towards digitizing using coordinates with asset bearing") {
    /*asset bearing in this case indicates towards which direction the traffic sign is facing, not the flow of traffic*/
    runWithRollback {
      val id = service.createFromCoordinates(3, 2, TRTrafficSignType.SpeedLimit, Some(100), Some(false), TrafficDirection.UnknownDirection, Some(225), None, vvHRoadlink2)
      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("100")
      asset.validityDirection should be (TowardsDigitizing.value)
      asset.bearing.get should be (45)
    }
  }

  test("Create traffic sign with direction against digitizing using coordinates with asset bearing") {
    /*asset bearing in this case indicates towards which direction the traffic sign is facing, not the flow of traffic*/
    runWithRollback {
      val id = service.createFromCoordinates(3, 4, TRTrafficSignType.SpeedLimit, Some(100), Some(false), TrafficDirection.UnknownDirection, Some(45), None, vvHRoadlink2)
      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("100")
      asset.validityDirection should be(AgainstDigitizing.value)
    }
  }

  test("two-sided traffic signs are effective in both directions ") {
    runWithRollback {
      val id = service.createFromCoordinates(3, 4, TRTrafficSignType.PedestrianCrossing, None, Some(true), TrafficDirection.UnknownDirection, Some(45), None, vvHRoadlink2)
      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("7")
      asset.validityDirection should be(BothDirections.value)

    }
  }

  test("Create traffic sign with additional information") {
    runWithRollback {
      val id = service.createFromCoordinates(3, 4, TRTrafficSignType.FreeWidth, None, Some(false), TrafficDirection.UnknownDirection, Some(45), Some("Info Test"), vvHRoadlink2)
      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("45")
      asset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.propertyValue should be ("Info Test")
      asset.validityDirection should be(AgainstDigitizing.value)
    }
  }

  test("Get trafficSigns by radius") {
    runWithRollback {
      val propertiesSpeedLimit = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("1"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue("80"))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Additional Info for test"))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 50.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(0.0, 20.0, 388553075, propertiesSpeedLimit, 1, None), testUser.username, roadLink)

      val assets = service.getTrafficSignByRadius(roadLink.geometry.last, 50, None)

      assets.size should be(1)

      val asset = assets.head

      asset.id should be(id)
      asset.linkId should be(388553075)
      asset.lon should be(0)
      asset.lat should be(20)
      asset.mValue should be(20)
      asset.floating should be(false)
      asset.municipalityCode should be(235)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("80")
      asset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.propertyValue should be ("Additional Info for test")
      asset.createdBy should be(Some(testUser.username))
      asset.createdAt shouldBe defined
    }
  }

  test("Get trafficSigns by radius and sign type") {
    runWithRollback {
      val propertiesSpeedLimit = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("1"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue("80"))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Additional Info for test"))))

      val propertiesMaximumRestrictions= Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("3"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue("10"))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 50.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      service.create(IncomingTrafficSign(0.0, 20.0, 388553075, propertiesSpeedLimit, 1, None), testUser.username, roadLink)
      service.create(IncomingTrafficSign(0.0, 20.0, 388553075, propertiesMaximumRestrictions, 1, None), testUser.username, roadLink)

      val assets = service.getTrafficSignByRadius(roadLink.geometry.last, 50, Some(TrafficSignTypeGroup.SpeedLimits))

      assets.size should be(1)

      val asset = assets.head
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("80")
      asset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.propertyValue should be ("Additional Info for test")
    }
  }

  test("Should return only assets with traffic restrictions"){
    runWithRollback {

      val properties = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("1"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue("80"))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Additional Info for test"))))

      val properties1 = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("10"))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Additional Info for test"))))


      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 1, None), testUser.username, roadLink)
      val id1 = service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties1, 1, None), testUser.username, roadLink)

      val assets = service.getTrafficSignsWithTrafficRestrictions(235, service.getRestrictionsEnumeratedValues)

      assets.find(_.id == id).size should be(0)
      assets.find(_.id == id1).size should be(1)
    }
  }

  test("Should call creation of manoeuvre actor on traffic sign creation"){
    runWithRollback {

      val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
      val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
      val mockUserProvider = MockitoSugar.mock[OracleUserProvider]
      val mockManoeuvreService = MockitoSugar.mock[ManoeuvreService]
      val mockProhibitionService = MockitoSugar.mock[ProhibitionService]

      val trService = new TrafficSignService(mockRoadLinkService, mockUserProvider, mockEventBus, mockManoeuvreService, mockProhibitionService) {
        override def withDynTransaction[T](f: => T): T = f
        override def withDynSession[T](f: => T): T = f
      }

      val properties = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue( TrafficSignType.NoLeftTurn.value.toString))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Additional Info for test"))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = trService.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 1, None), testUser.username, roadLink)
      val asset = trService.getPersistedAssetsByIds(Set(id)).head

      verify(mockEventBus, times(1)).publish("manoeuvre:create",TrafficSignInfo(asset.id, asset.linkId, asset.validityDirection, TrafficSignType.NoLeftTurn.value, asset.mValue, roadLink))
    }
  }

  test("Should call expire of manoeuvre actor on traffic sign expirinf"){
    runWithRollback {

      val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
      val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
      val mockUserProvider = MockitoSugar.mock[OracleUserProvider]
      val mockManoeuvreService = MockitoSugar.mock[ManoeuvreService]
      val mockProhibitionService = MockitoSugar.mock[ProhibitionService]

      val trService = new TrafficSignService(mockRoadLinkService, mockUserProvider, mockEventBus, mockManoeuvreService, mockProhibitionService) {
        override def withDynTransaction[T](f: => T): T = f
        override def withDynSession[T](f: => T): T = f
      }

      val properties = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue(TrafficSignType.NoLeftTurn.value.toString))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Additional Info for test"))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = trService.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 1, None), testUser.username, roadLink)
      val asset = trService.getPersistedAssetsByIds(Set(id)).head

      verify(mockEventBus, times(1)).publish("manoeuvre:create", TrafficSignInfo(asset.id, asset.linkId, asset.validityDirection, TrafficSignType.NoLeftTurn.value, asset.mValue, roadLink))

      trService.expire(id, "test_user")
      verify(mockEventBus, times(1)).publish("manoeuvre:expire", id)
    }
  }

  test("Pedestrian crossings are filtered") {
    runWithRollback {
      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(), Nil))


      val linkId1 = 388553075
      val linkId2 = 388553074
      val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val adjacentRoadLink = RoadLink(linkId2, Seq(Point(0.0, 20.0), Point(0.0, 40.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val properties = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("7"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue(""))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Pedestrian crossing for test purpose"))))

      service.create(IncomingTrafficSign(0.0, 20.0, linkId1, properties, BothDirections.value, None), testUser.username, roadLink)
      service.create(IncomingTrafficSign(0.0, 20.0, linkId1, properties, BothDirections.value, None), batchProcessName, roadLink)
      service.create(IncomingTrafficSign(0.0, 20.0, linkId1, properties, TowardsDigitizing.value, None), batchProcessName, roadLink)
      service.create(IncomingTrafficSign(0.0, 20.0, linkId1, properties, AgainstDigitizing.value, None), batchProcessName, roadLink)
      service.create(IncomingTrafficSign(0.0, 19.0, linkId1, properties, BothDirections.value, None), batchProcessName, roadLink)
      service.create(IncomingTrafficSign(0.0, 16.9, linkId1, properties, BothDirections.value, None), batchProcessName, roadLink)

      service.create(IncomingTrafficSign(0.0, 21.0, linkId2, properties, BothDirections.value, None), batchProcessName, adjacentRoadLink)
      service.create(IncomingTrafficSign(0.0, 21.0, linkId2, properties, TowardsDigitizing.value, None), batchProcessName, adjacentRoadLink)
      service.create(IncomingTrafficSign(0.0, 21.0, linkId2, properties, AgainstDigitizing.value, None), batchProcessName, adjacentRoadLink)
      service.create(IncomingTrafficSign(0.0, 21.0, linkId2, properties, BothDirections.value, None), batchProcessName, adjacentRoadLink)

      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(0.0, 0.0), Point(0.0, 40.0)))

      val assetsOnLinkId1_vd1 = result.toList.groupBy(_.linkId).find(_._1 == linkId1).map(_._2).getOrElse(Seq.empty[PersistedTrafficSign]).filter(asset => asset.validityDirection == 1)
      val assetsOnLinkId2_vd1 = result.toList.groupBy(_.linkId).find(_._1 == linkId2).map(_._2).getOrElse(Seq.empty[PersistedTrafficSign]).filter(asset => asset.validityDirection == 1)
      val assetsOnLinkId2_vd2 = result.toList.groupBy(_.linkId).find(_._1 == linkId2).map(_._2).getOrElse(Seq.empty[PersistedTrafficSign]).filter(asset => asset.validityDirection == 2)

      val signGroupOnLinkId1 = assetsOnLinkId1_vd1.sortBy(_.lat).lift(1).head

      signGroupOnLinkId1.propertyData.filter(_.id == 0).head.values.head.propertyValue.toInt should be (2)
      assetsOnLinkId2_vd1.head.propertyData.filter(_.id == 0).head.values.head.propertyValue.toInt should be (2)
      assetsOnLinkId2_vd2.head.propertyData.filter(_.id == 0).head.values.head.propertyValue.toInt should be (1)
    }
  }

  test("Prevent the creations of duplicated signs") {
    runWithRollback {
      val originalTrafficSignId = service.createFromCoordinates(5, 4, TRTrafficSignType.NoPedestrians, None, Some(false), TrafficDirection.UnknownDirection, None, Some("Original Traffic Sign!"), vvHRoadlink2)
      val assetsInRadius = service.getTrafficSignByRadius(Point(5, 4), 10, None)
      assetsInRadius.size should be(1)
      val assetO = assetsInRadius.head
      assetO.id should be(originalTrafficSignId)
      assetO.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be("24")
      assetO.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.propertyValue should be("Original Traffic Sign!")


      val duplicatedTrafficSignId = service.createFromCoordinates(6, 4, TRTrafficSignType.NoPedestrians, None, Some(false), TrafficDirection.UnknownDirection, None, Some("Non Duplicated Traffic Sign!"), vvHRoadlink2)
      val assetsInRadius2 = service.getTrafficSignByRadius(Point(5, 4), 10, None)
      assetsInRadius2.size should be(1)
      val assetD = assetsInRadius2.head
      assetD.id should be(duplicatedTrafficSignId)
      assetD.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be("24")
      assetD.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.propertyValue should be("Non Duplicated Traffic Sign!")
    }

  }

  test("get by distance with same roadLink, trafficType and Direction") {
      val speedLimitProp = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.SpeedLimit.value.toString))))
      val speedLimitZoneProp = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.SpeedLimitZone.value.toString))))
      val trafficSigns = Seq(
        PersistedTrafficSign(1, 1002l, 2, 0, 2, false, 0, 235, speedLimitProp, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(2, 1002l, 4, 0, 4, false, 0, 235, speedLimitProp, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(3, 1002l, 12.1, 0, 12, false, 0, 235, speedLimitProp, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(4, 1002l, 2, 9, 12, false, 0, 235, speedLimitProp, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(5, 1002l, 5, 0, 5, false, 0, 235, speedLimitZoneProp, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(6, 1003l, 4, 0, 4, false, 0, 235, speedLimitProp, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(7, 1002l, 4, 0, 4, false, 0, 235, speedLimitProp, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
      )

    val groupedAssets = trafficSigns.groupBy(_.linkId)
    val result = service.getTrafficSignsByDistance(trafficSigns.find(_.id == 1).get, groupedAssets, 10)
    result should have size 3
    result.exists(_.id == 1) should be (true)
    result.exists(_.id == 2) should be (true)
    result.exists(_.id == 3) should be (false) //more than 10 meter
    result.exists(_.id == 4) should be (true)
    result.exists(_.id == 5) should be (false) //different sign type
    result.exists(_.id == 6) should be (false) //different linkId
    result.exists(_.id == 7) should be (false) //different direction
  }
}
