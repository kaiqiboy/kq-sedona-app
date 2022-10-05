import org.apache.sedona.core.enums.{GridType, IndexType}
import org.apache.sedona.core.serde.SedonaKryoRegistrator
import org.apache.sedona.sql.utils.{Adapter, SedonaSQLRegistrator}
import org.apache.spark.serializer.KryoSerializer
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.locationtech.jts.geom.Geometry

import scala.Numeric.Implicits._

object LoadBalance {
  case class E(shape: String, timeStamp: Array[Long], v: Option[String], d: String)

  case class TrajPoint(lon: Double, lat: Double, t: Array[Long], v: Option[String])

  case class T(points: Array[TrajPoint], d: String)

  def mean[T: Numeric](xs: Iterable[T]): Double = xs.sum.toDouble / xs.size

  def variance[T: Numeric](xs: Iterable[T]): Double = {
    val avg = mean(xs)

    xs.map(_.toDouble).map(a => math.pow(a - avg, 2)).sum / xs.size
  }

  def stdDev[T: Numeric](xs: Iterable[T]): Double = math.sqrt(variance(xs))

  def main(args: Array[String]): Unit = {
    val dataFile = args(0)
    val numPartitions = args(1).toInt
    val mode = args(2)
    val spark = SparkSession.builder()
      .master("local")
      .appName("GeoSparkAnomalyExp")
      .config("spark.serializer", classOf[KryoSerializer].getName)
      .config("spark.kryo.registrator", classOf[SedonaKryoRegistrator].getName)
      .getOrCreate()
    SedonaSQLRegistrator.registerAll(spark)
    val sc = spark.sparkContext
    sc.setLogLevel("ERROR")

    if (mode == "event") {
      val pointDf = readEvent(dataFile)
      val pointRDD = Adapter.toSpatialRdd(pointDf, "location")
      pointRDD.analyze()
      pointRDD.buildIndex(IndexType.RTREE, false)
      pointRDD.spatialPartitioning(GridType.KDBTREE, numPartitions)
      val combinedRDD = pointRDD.rawSpatialRDD.rdd.map[(Geometry, String)](f => (f, f.getUserData.asInstanceOf[String]))
        .map {
          case (geoms, tsString) =>
            val timestamp = tsString.split("\t").head.toLong
            val id = tsString.split("\t").head
            (geoms, timestamp, id)
        }
      val sizes = combinedRDD.mapPartitions(x => Iterator(x.length)).collect.filter(x => x != 0)
      println(sizes.deep)
      println(stdDev(sizes) / mean(sizes))

      val a = combinedRDD.mapPartitions {
        x =>
          val points = x.toArray
          if (points.length == 0) Iterator()
          else {
            val xs = points.map(x => x._1.getCoordinates()(0).x)
            val xMin = xs.min
            val xMax = xs.max
            val ys = points.map(x => x._1.getCoordinates()(0).y)
            val yMin = ys.min
            val yMax = ys.max
            val ts = points.map(x => x._2.toLong)
            val tMin = ts.min
            val tMax = ts.max
            Iterator((xMin, xMax, yMin, yMax, tMin, tMax))
          }
      }
      val aggregated = a.collect
      //    println(aggregated.deep)
      val area1 = aggregated.map(x => (x._2 - x._1) * (x._4 - x._3) * (x._6 - x._5))
      val a1 = area1.sum

      val a2 = (aggregated.map(_._2).max - aggregated.map(_._1).min) * (aggregated.map(_._4).max - aggregated.map(_._3).min) * (aggregated.map(_._6).max - aggregated.map(_._5).min)

      println(a1 / a2)
    }
    else if (mode == "traj") {
      val trajDf = readTraj(dataFile)
      val trajRDD = Adapter.toSpatialRdd(trajDf, "linestring")
      trajRDD.analyze()
      trajRDD.buildIndex(IndexType.RTREE, false)
      trajRDD.spatialPartitioning(GridType.KDBTREE, numPartitions)
      val combinedRDD = trajRDD.rawSpatialRDD.rdd.map[(Geometry, String)](f => (f, f.getUserData.asInstanceOf[String]))
        .map {
          case (geoms, tsString) =>
            val timestamp = tsString.split("\t").head.toLong
            val id = tsString.split("\t").head
            (geoms, timestamp, id)
        }
      val sizes = combinedRDD.mapPartitions(x => Iterator(x.length)).collect.filter(x => x != 0)
      println(sizes.deep)
      println(stdDev(sizes) / mean(sizes))

      val a = combinedRDD.mapPartitions {
        x =>
          val points = x.toArray
          if (points.length == 0) Iterator()
          else {
            val xs = points.map(x => x._1.getCoordinates()(0).x)
            val xMin = xs.min
            val xMax = xs.max
            val ys = points.map(x => x._1.getCoordinates()(0).y)
            val yMin = ys.min
            val yMax = ys.max
            val ts = points.map(x => x._2.toLong)
            val tMin = ts.min
            val tMax = ts.max
            Iterator((xMin, xMax, yMin, yMax, tMin, tMax))
          }
      }
      val aggregated = a.collect
      //    println(aggregated.deep)
      val area1 = aggregated.map(x => (x._2 - x._1) * (x._4 - x._3) * (x._6 - x._5))
      val a1 = area1.sum

      val a2 = (aggregated.map(_._2).max - aggregated.map(_._1).min) * (aggregated.map(_._4).max - aggregated.map(_._3).min) * (aggregated.map(_._6).max - aggregated.map(_._5).min)

      println(a1 / a2)
    }
    sc.stop()
  }

  def readEvent(file: String): DataFrame = {
    val spark = SparkSession.builder().getOrCreate()
    val readDs = spark.read.parquet(file)
    readDs.createOrReplaceTempView("input")
    val sqlQuery = "SELECT ST_GeomFromWKT(input.shape) AS location, CAST(element_at(input.timeStamp, 1) AS STRING) AS timestamp, input.d AS id FROM input"
    val pointDF = spark.sql(sqlQuery)
    pointDF //.repartition(numPartitions)
  }

  def readTraj(file: String): DataFrame = {
    val spark = SparkSession.builder().getOrCreate()
    val readDs = spark.read.parquet(file)
    import spark.implicits._
    val trajRDD = readDs.as[T].rdd.map(t => {
      val string = t.points.flatMap(e => Array(e.lon, e.lat)).mkString(",")
      val tsArray = t.points.flatMap(e => Array(e.t(0))).mkString(",")
      (string, t.d, tsArray)
    })
    val df = trajRDD.toDF("string", "id", "tsArray")
    df.createOrReplaceTempView("input")
    val sqlQuery = "SELECT ST_LineStringFromText(CAST(input.string AS STRING), ',') AS linestring, CAST(input.id AS STRING) AS id, CAST(input.tsArray AS STRING)  AS tsArray FROM input"
    val a = spark.sql(sqlQuery)
    a
  }

}
