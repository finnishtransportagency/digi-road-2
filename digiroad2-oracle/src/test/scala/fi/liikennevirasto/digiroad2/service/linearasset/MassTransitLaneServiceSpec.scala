package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, SideCodeAdjustment}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}

class MassTransitLaneServiceSpec extends DynamicLinearTestSupporter {

  object mtlServiceWithDao extends MassTransitLaneService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: OracleAssetDao = mockAssetDao
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = mVLinearAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }
  case class TestAssetInfo(newLinearAsset: NewLinearAsset, typeId: Int)

  test("Adjust projected mtl asset with creation"){

   val massTransitLaneValue = DynamicValue(DynamicAssetValue(Seq(
     DynamicProperty("public_validity_period", "time_period", false, Seq(DynamicPropertyValue(Map("days" -> 1,
       "startHour" -> 2,
       "endHour" -> 10,
       "startMinute" -> 10,
       "endMinute" -> 20))))
        )))

    adjustProjectedAssetWithCreation(TestAssetInfo(NewLinearAsset(5000l, 0, 150, massTransitLaneValue, SideCode.AgainstDigitizing.value, 0, None), MassTransitLane.typeId))

  }

  def adjustProjectedAssetWithCreation(assetInfoCount: TestAssetInfo) : Unit = {
    val assetInfo = assetInfoCount
    val oldLinkId = 5000
    val newLinkId = 6000
    val municipalityCode = 444
    val functionalClass = 1
    val geom = List(Point(0, 0), Point(300, 0))
    val len = GeometryUtils.geometryLength(geom)

    val roadLinks = Seq(RoadLink(oldLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )
    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId), Some(newLinkId), 1204467577, 1, Some(0), Some(150), Some(100), Some(200), 1461970812000L))

    runWithRollback {
      val assetId = mtlServiceWithDao.create(Seq(assetInfo.newLinearAsset), assetInfo.typeId, "KX1", 0).head

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[Int])).thenReturn((roadLinks, changeInfo))
      mtlServiceWithDao.getByMunicipality(assetInfo.typeId, municipalityCode)

      withClue("assetName " + AssetTypeInfo.apply(assetInfo.typeId).layerName) {
        verify(mockEventBus, times(1))
          .publish("dynamicAsset:update", ChangeSet(Set(),List(),List(),List(),Set()))

        val captor = ArgumentCaptor.forClass(classOf[Seq[PersistedLinearAsset]])
        verify(mockEventBus, times(1)).publish(org.mockito.ArgumentMatchers.eq("dynamicAsset:saveProjectedAssets"), captor.capture())

        val toBeComparedProperties = assetInfo.newLinearAsset.value.asInstanceOf[DynamicValue].value.properties
        val projectedAssets = captor.getValue
        projectedAssets.length should be(1)
        val projectedAsset = projectedAssets.head
        projectedAsset.id should be(0)
        projectedAsset.linkId should be(6000)
        projectedAsset.value.get.asInstanceOf[DynamicValue].value.properties.foreach { property =>
          val existingProperty = toBeComparedProperties.find(p => p.publicId == property.publicId)
          existingProperty.get.values.forall { value =>
            toBeComparedProperties.asInstanceOf[Seq[DynamicProperty]].exists(prop => prop.values.contains(value))
          }
        }
      }
    }
  }
}