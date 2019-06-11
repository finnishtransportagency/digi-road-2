package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.{GeometryUtils, PersistedPoint, PersistedPointAsset, Point}
import fi.liikennevirasto.digiroad2.{PersistedPoint, PersistedPointAsset, Point, PointAssetOperations}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.service.pointasset.IncomingObstacle
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._

case class ObstacleRow(id: Long, linkId: Long,
                       lon: Double, lat: Double,
                       mValue: Double, floating: Boolean,
                       vvhTimeStamp: Long,
                       municipalityCode: Int,
                       property: PropertyRow,
                       createdBy: Option[String] = None,
                       createdAt: Option[DateTime] = None,
                       modifiedBy: Option[String] = None,
                       modifiedAt: Option[DateTime] = None,
                       expired: Boolean = false,
                       linkSource: LinkGeomSource)

case class Obstacle(id: Long, linkId: Long,
                    lon: Double, lat: Double,
                    mValue: Double, floating: Boolean,
                    vvhTimeStamp: Long,
                    municipalityCode: Int,
                    propertyData: Seq[Property],
                    createdBy: Option[String] = None,
                    createdAt: Option[DateTime] = None,
                    modifiedBy: Option[String] = None,
                    modifiedAt: Option[DateTime] = None,
                    expired: Boolean = false,
                    linkSource: LinkGeomSource) extends PersistedPoint

object OracleObstacleDao {

  private def query() = {
    """
      select a.id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp, a.municipality_code, p.id, p.public_id, p.property_type, p.required, ev.value,
      case
        when ev.name_fi is not null then ev.name_fi
        else null
      end as display_value, a.created_by, a.created_date, a.modified_by, a.modified_date, case when a.valid_to <= sysdate then 1 else 0 end as expired, pos.link_source
      from asset a
      join asset_link al on a.id = al.asset_id
      join lrm_position pos on al.position_id = pos.id
      join property p on a.asset_type_id = p.asset_type_id
      left join multiple_choice_value mcv ON mcv.asset_id = a.id and mcv.property_id = p.id AND p.property_type = 'checkbox'
      left join single_choice_value scv on scv.asset_id = a.id and scv.property_id = p.id and p.property_type = 'single_choice'
      left join enumerated_value ev on scv.enumerated_value_id = ev.id or mcv.enumerated_value_id = ev.ID
    """
  }

  def fetchByFilterWithExpired(queryFilter: String => String): Seq[Obstacle] = {
    val queryWithFilter = queryFilter(query())
    queryToObstacle(queryWithFilter)
  }

  // This works as long as there is only one (and exactly one) property (currently type) for obstacles and up to one value
  def fetchByFilter(queryFilter: String => String): Seq[Obstacle] = {
    val queryWithFilter = queryFilter(query()) + " and (a.valid_to > sysdate or a.valid_to is null)"
    queryToObstacle(queryWithFilter)
  }

  def assetRowToProperty(assetRows: Iterable[ObstacleRow]): Seq[Property] = {
    assetRows.groupBy(_.property.propertyId).map { case (key, rows) =>
      val row = rows.head
      Property(
        id = key,
        publicId = row.property.publicId,
        propertyType = row.property.propertyType,
        required = row.property.propertyRequired,
        values = rows.flatMap { assetRow =>
           Seq(PropertyValue(assetRow.property.propertyValue, Option(assetRow.property.propertyDisplayValue)))
        }.toSeq)
    }.toSeq
  }

  private def queryToObstacle(query: String): Seq[Obstacle] = {
    val rows = StaticQuery.queryNA[ObstacleRow](query)(getPointAsset).iterator.toSeq

    rows.groupBy(_.id).map { case (id, signRows) =>
      val row = signRows.head
      val properties: Seq[Property] = assetRowToProperty(signRows)

      id -> Obstacle(id = row.id, linkId = row.linkId, lon = row.lon, lat = row.lat, mValue = row.mValue,
        floating = row.floating, vvhTimeStamp = row.vvhTimeStamp, municipalityCode = row.municipalityCode, properties,
        createdBy = row.createdBy, createdAt = row.createdAt, modifiedBy = row.modifiedBy, modifiedAt = row.modifiedAt,
        expired = row.expired, linkSource = row.linkSource)
    }.values.toSeq
  }

  implicit val getPointAsset = new GetResult[ObstacleRow] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val linkId = r.nextLong()
      val point = r.nextBytesOption().map(bytesToPoint).get
      val mValue = r.nextDouble()
      val floating = r.nextBoolean()
      val vvhTimeStamp = r.nextLong()
      val municipalityCode = r.nextInt()
      val propertyId = r.nextLong
      val propertyPublicId = r.nextString
      val propertyType = r.nextString
      val propertyRequired = r.nextBoolean
      val propertyValue = r.nextLongOption()
      val propertyDisplayValue = r.nextStringOption()
      val property = new PropertyRow(
        propertyId = propertyId,
        publicId = propertyPublicId,
        propertyType = propertyType,
        propertyRequired = propertyRequired,
        propertyValue = propertyValue.getOrElse(propertyDisplayValue.getOrElse("")).toString,
        propertyDisplayValue = propertyDisplayValue.orNull)
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean()
      val linkSource = r.nextInt()

      ObstacleRow(id, linkId, point.x, point.y, mValue, floating, vvhTimeStamp, municipalityCode, property, createdBy, createdDateTime, modifiedBy, modifiedDateTime, expired, LinkGeomSource(linkSource))
    }
  }

  def create(obstacle: IncomingObstacle, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long, linkSource: LinkGeomSource): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($id, 220, $username, sysdate, $municipality)

        into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source)
        values ($lrmPositionId, $mValue, ${obstacle.linkId}, $adjustmentTimestamp, ${linkSource.value})

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

      select * from dual
    """.execute
    updateAssetGeometry(id, Point(obstacle.lon, obstacle.lat))

    obstacle.propertyData.map(propertyWithTypeAndId(Obstacles.typeId)).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }
    id
  }

  def create(obstacle: IncomingObstacle, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long, linkSource: LinkGeomSource, createdByFromUpdate: Option[String] = Some(""), createdDateTimeFromUpdate: Option[DateTime]): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code, modified_by, modified_date)
        values ($id, ${Obstacles.typeId}, $createdByFromUpdate, $createdDateTimeFromUpdate, $municipality, $username, sysdate)

        into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source, modified_date)
        values ($lrmPositionId, $mValue, ${obstacle.linkId}, $adjustmentTimestamp, ${linkSource.value}, sysdate)

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

      select * from dual
    """.execute
    updateAssetGeometry(id, Point(obstacle.lon, obstacle.lat))

    obstacle.propertyData.map(propertyWithTypeAndId(Obstacles.typeId)).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }

    id
  }

  def update(id: Long, obstacle: IncomingObstacle, mValue: Double, username: String, municipality: Int, adjustedTimeStampOption: Option[Long] = None, linkSource: LinkGeomSource) = {
    sqlu""" update asset set municipality_code = $municipality where id = $id """.execute
    updateAssetModified(id, username).execute
    updateAssetGeometry(id, Point(obstacle.lon, obstacle.lat))
    obstacle.propertyData.map(propertyWithTypeAndId(Obstacles.typeId)).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }

    adjustedTimeStampOption match {
      case Some(adjustedTimeStamp) =>
        sqlu"""
          update lrm_position
           set
           start_measure = $mValue,
           link_id = ${obstacle.linkId},
           adjusted_timestamp = ${adjustedTimeStamp},
           link_source = ${linkSource.value}
          where id = (select position_id from asset_link where asset_id = $id)
        """.execute
      case _ =>
        sqlu"""
          update lrm_position
           set
           start_measure = $mValue,
           link_id = ${obstacle.linkId},
           link_source = ${linkSource.value}
          where id = (select position_id from asset_link where asset_id = $id)
        """.execute
    }

    id
  }

  def selectFloatings(floating: Int, lastIdUpdate: Long, batchSize: Int) : Seq[Obstacle] ={
    val query =
      """
        select * from (
          select a.id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp, a.municipality_code, p.id, p.public_id, p.property_type, p.required, ev.value,
            case
              when ev.name_fi is not null then ev.name_fi
              else null
        end as display_value, a.created_by, a.created_date, a.modified_by,
        a.modified_date, case when a.valid_to <= sysdate then 1 else 0 end as expired, pos.link_source
       from asset a
       join asset_link al on a.id = al.asset_id
       join lrm_position pos on al.position_id = pos.id
       join property p on a.asset_type_id = p.asset_type_id
       left join multiple_choice_value mcv ON mcv.asset_id = a.id and mcv.property_id = p.id AND p.property_type = 'checkbox'
       left join single_choice_value scv on scv.asset_id = a.id and scv.property_id = p.id and p.property_type = 'single_choice'
       left join enumerated_value ev on scv.enumerated_value_id = ev.id or mcv.enumerated_value_id = ev.ID
    """

    val queryWithFilter = query + s"where a.asset_type_id = 220 and a.floating = $floating and " +
      s"(a.valid_to > sysdate or a.valid_to is null) and a.id > $lastIdUpdate order by a.id asc) where ROWNUM <= $batchSize"
    queryToObstacle(queryWithFilter)
  }

  def updateFloatingAsset(obstacleUpdated: Obstacle) = {
    val id = obstacleUpdated.id

    sqlu"""update asset set municipality_code = ${obstacleUpdated.municipalityCode}, floating =  ${obstacleUpdated.floating} where id = $id""".execute

    updateAssetModified(id, obstacleUpdated.modifiedBy.get).execute
    updateAssetGeometry(id, Point(obstacleUpdated.lon, obstacleUpdated.lat))

    obstacleUpdated.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet.map(propertyWithTypeAndId(Obstacles.typeId)).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }

    sqlu"""
      update lrm_position
       set
       start_measure = ${obstacleUpdated.mValue},
       link_id = ${obstacleUpdated.linkId}
       where id = (select position_id from asset_link where asset_id = $id)
    """.execute
  }

  private def getPropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("esterakennelma").first
  }


  def propertyWithTypeAndId(typeId: Int)(property: SimplePointAssetProperty): Tuple3[String, Option[Long], SimplePointAssetProperty] = {
    val propertyId = StaticQuery.query[(String, Int), Long](propertyIdByPublicIdAndTypeId).apply(property.publicId, typeId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
    (StaticQuery.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
  }

  def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsSingleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  def textPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsTextProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  def multipleChoiceValueDoesNotExist(assetId: Long, propertyId: Long): Boolean = {
    StaticQuery.query[(Long, Long), Long](existsMultipleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }


  def createOrUpdateProperties(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PointAssetValue]) {
    propertyType match {
      case Text | LongText =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty) {
          deleteTextProperty(assetId, propertyId).execute
        } else if (textPropertyValueDoesNotExist(assetId, propertyId)) {
          insertTextProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue).execute
        } else {
          updateTextProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue).execute
        }
      case SingleChoice =>
        if (propertyValues.size != 1) throw new IllegalArgumentException("Single choice property must have exactly one value. publicId: " + propertyPublicId)
        if (singleChoiceValueDoesNotExist(assetId, propertyId)) {
          insertSingleChoiceProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        } else {
          updateSingleChoiceProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        }
      case AdditionalPanelType =>
        if (propertyValues.size > 3) throw new IllegalArgumentException("A maximum of 3 " + propertyPublicId + " allowed per traffic sign.")
        deleteAdditionalPanelProperty(assetId).execute
        propertyValues.foreach{value =>
          insertAdditionalPanelProperty(assetId, value.asInstanceOf[AdditionalPanel]).execute
        }
      case CheckBox =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Multiple choice only allows values between 0 and 1.")
        if(multipleChoiceValueDoesNotExist(assetId, propertyId)) {
          insertMultipleChoiceValue(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        } else {
          updateMultipleChoiceValue(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        }
      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }
}



