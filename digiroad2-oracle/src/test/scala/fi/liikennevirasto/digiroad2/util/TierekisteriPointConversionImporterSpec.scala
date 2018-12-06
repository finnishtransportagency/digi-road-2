package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri.{TierekisteriAssetData, _}
import fi.liikennevirasto.digiroad2.client.tierekisteri.importer._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHClient,VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, OracleAssetDao, RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.service.linearasset.Measures
import fi.liikennevirasto.digiroad2.service.{RoadAddressesService, RoadLinkService}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class TierekisteriPointConversionImporterSpec extends FunSuite with Matchers  {

  val mockAssetDao: OracleAssetDao = MockitoSugar.mock[OracleAssetDao]
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressesService]
  val mockMunicipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockTRClient: TierekisteriWeightLimitAssetClient = MockitoSugar.mock[TierekisteriWeightLimitAssetClient]
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient: VVHClient = MockitoSugar.mock[VVHClient]
  val mockTierekisteriAssetDataClient: TierekisteriAssetDataClient = MockitoSugar.mock[TierekisteriAssetDataClient]

  class TestTierekisteriPointConversionImporter1 extends TierekisteriPointConversionImporter {
    override def typeId: Int = 999
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
    override def assetName: String = "assetTest"
    override type TierekisteriClientType = TierekisteriAssetDataClient
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressesService = mockRoadAddressService
    override val tierekisteriClient: TierekisteriAssetDataClient = mockTierekisteriAssetDataClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient

    var createObject: Seq[(VVHRoadlink, ViiteRoadAddress, AddressSection, Measures, TierekisteriAssetData)] = Seq()

    override protected def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData) : Unit = {
      createObject = List.concat(createObject , Seq((vvhRoadlink, roadAddress, section, measures, trAssetData)))
    }

    def getCreatedValues: Seq[(VVHRoadlink, ViiteRoadAddress, AddressSection, Measures, TierekisteriAssetData)] = {
      createObject
    }

    def createAssetTest(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink]) =
      super.createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink])

    override val allowedVerticalLevel : Seq[Int] = { Seq(1, 2, 3, 4)}
  }

  case class TierekisteriWeightLimitDataTest(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long, track: Track, startAddressMValue: Long, endAddressMValue: Long,
                                          totalWeight: Option[Int], trailerTruckWeight: Option[Int], axleWeight: Option[Int], bogieWeight: Option[Int], threeBogieWeight: Option[Int])  extends TierekisteriAssetData


  test("create linear using point main method") {
    val tierekisteriPointConversionImporter = new TestTierekisteriPointConversionImporter1()

    val roadNumber: Int = 4
    val startRoadPartNumber: Int = 203
    val startAddressMValue: Int = 3184
    val endAddressMValue: Int = 3400
    val track: Track = Track.RightSide

    val trAssetData = TierekisteriWeightLimitDataTest(roadNumber, startRoadPartNumber, startRoadPartNumber, track, startAddressMValue, startAddressMValue , None, None, Some(1000), None, None)
    val section = AddressSection(roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, Some(endAddressMValue))
    val vvhRoadLink = VVHRoadlink(5001, 235, Seq(Point(0,0), Point(10,0)), State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = Map("VERTICALLEVEL" -> 1))
    val roadAddress = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)

    tierekisteriPointConversionImporter.createAssetTest(section, trAssetData.asInstanceOf[tierekisteriPointConversionImporter.TierekisteriAssetData], Seq(roadAddress).groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track)), Seq(vvhRoadLink))

    tierekisteriPointConversionImporter.getCreatedValues.size should be (1)
    tierekisteriPointConversionImporter.getCreatedValues.foreach { case (roadLink, viiteRoadAddress, addressSection, assetMeasures, tierekisteriAssetData) =>
      viiteRoadAddress should be (roadAddress)
      roadLink should be (vvhRoadLink)
      addressSection should be (section)
      tierekisteriAssetData should be (trAssetData)
    }
  }

  test("create linear on adjacent link using point main method") {
    val tierekisteriPointConversionImporter = new TestTierekisteriPointConversionImporter1()

    val roadNumber: Int = 4
    val startRoadPartNumber: Int = 203
    val startAddressMValue: Int = 3184
    val endAddressMValue: Int = 3400
    val track: Track = Track.RightSide

    val trAssetData = TierekisteriWeightLimitDataTest(roadNumber, startRoadPartNumber, startRoadPartNumber, track, startAddressMValue, startAddressMValue , None, None, Some(1000), None, None)
    val section = AddressSection(roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, Some(endAddressMValue))
    val vvhRoadLink = VVHRoadlink(5001, 235, Seq(Point(0,0), Point(10,0)), State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
    val roadAddress = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)

    val adjacentVVHRoadLink = VVHRoadlink(5002, 235, Seq(Point(10,0), Point(20,0)), State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = Map("VERTICALLEVEL" -> 1))
    val mappedRoadLinks = Seq(adjacentVVHRoadLink, vvhRoadLink)

    tierekisteriPointConversionImporter.createAssetTest(section, trAssetData.asInstanceOf[tierekisteriPointConversionImporter.TierekisteriAssetData], Seq(roadAddress).groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track)), mappedRoadLinks)

    tierekisteriPointConversionImporter.getCreatedValues.size should be (1)
    tierekisteriPointConversionImporter.getCreatedValues.foreach { case (roadLink, viiteRoadAddress, addressSection, assetMeasures, tierekisteriAssetData) =>
      viiteRoadAddress should be (roadAddress)
      roadLink should be (adjacentVVHRoadLink)
      addressSection should be (section)
      tierekisteriAssetData should be (trAssetData)
    }
  }

  test("create linear on nearest adjacent link using point main method") {
    val tierekisteriPointConversionImporter = new TestTierekisteriPointConversionImporter1()

    val roadNumber: Int = 4
    val startRoadPartNumber: Int = 203
    val startAddressMValue: Int = 3184
    val endAddressMValue: Int = 3194
    val track: Track = Track.RightSide

    val trAssetData = TierekisteriWeightLimitDataTest(roadNumber, startRoadPartNumber, startRoadPartNumber, track, 2, 2 , None, None, Some(1000), None, None)
    val section = AddressSection(roadNumber, startRoadPartNumber, track, startAddressMValue, Some(endAddressMValue))
    val vvhRoadLink = VVHRoadlink(5001, 235, Seq(Point(5,0), Point(10,0)), State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
    val roadAddress = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, track, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)

    val adjacentVVHRoadLink = Seq(VVHRoadlink(5000, 235, Seq(Point(0,0), Point(5,0)), State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = Map("VERTICALLEVEL" -> 1)),
                                  VVHRoadlink(5002, 235, Seq(Point(10,0), Point(20,0)), State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = Map("VERTICALLEVEL" -> 1)))

    val mappedRoadLinks = adjacentVVHRoadLink ++ Seq(vvhRoadLink)

    tierekisteriPointConversionImporter.createAssetTest(section, trAssetData.asInstanceOf[tierekisteriPointConversionImporter.TierekisteriAssetData], Seq(roadAddress).groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track)), mappedRoadLinks)

    tierekisteriPointConversionImporter.getCreatedValues.size should be (1)
    tierekisteriPointConversionImporter.getCreatedValues.foreach { case (roadLink, viiteRoadAddress, addressSection, assetMeasures, tierekisteriAssetData) =>
      viiteRoadAddress should be (roadAddress)
      roadLink.linkId should be (5000)
      addressSection should be (section)
      tierekisteriAssetData should be (trAssetData)
    }
  }

  test("not create linear when 'silta' is more than 50m far") {
    val tierekisteriPointConversionImporter = new TestTierekisteriPointConversionImporter1()

    val roadNumber: Int = 4
    val startRoadPartNumber: Int = 203
    val startAddressMValue: Int = 3184
    val endAddressMValue: Int = 3194
    val track: Track = Track.RightSide

    val trAssetData = TierekisteriWeightLimitDataTest(roadNumber, startRoadPartNumber, startRoadPartNumber, track, 1, 1 , None, None, Some(1000), None, None)
    val section = AddressSection(roadNumber, startRoadPartNumber, track, startAddressMValue, Some(endAddressMValue))
    val vvhRoadLink = VVHRoadlink(5001, 235, Seq(Point(0,0), Point(10,0)), State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
    val roadAddress = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, track, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)

    val adjacentVVHRoadLink = Seq(VVHRoadlink(5000, 235, Seq(Point(10,0), Point(52,0)), State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
      VVHRoadlink(5002, 235, Seq(Point(52,0), Point(60,0)), State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = Map("VERTICALLEVEL" -> 1)))

    val mappedRoadLinks = adjacentVVHRoadLink ++ Seq(vvhRoadLink)

    tierekisteriPointConversionImporter.createAssetTest(section, trAssetData.asInstanceOf[tierekisteriPointConversionImporter.TierekisteriAssetData], Seq(roadAddress).groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track)), mappedRoadLinks)

    tierekisteriPointConversionImporter.getCreatedValues.size should be (0)
  }
}
